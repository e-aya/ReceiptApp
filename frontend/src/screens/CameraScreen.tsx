import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  ActivityIndicator, SafeAreaView, Alert,
} from 'react-native';
import {
  Camera, useCameraDevice, useCameraPermission,
} from 'react-native-vision-camera';
import { receiptStore } from '../store/receiptStore';

// ★ 本番APIのURL
const API_BASE_URL = 'https://receiptapp-api-service-production.up.railway.app';
const USER_ID = 'test-user-001';

interface Props {
  onNavigateToReview: () => void;
}

export default function CameraScreen({ onNavigateToReview }: Props) {
  const { hasPermission, requestPermission } = useCameraPermission();
  const device = useCameraDevice('back');
  const camera = useRef<Camera>(null);
  const [isTakingPhoto, setIsTakingPhoto] = useState(false);
  const [capturedCount, setCapturedCount] = useState(0);

  useEffect(() => {
    if (!hasPermission) requestPermission();
  }, [hasPermission]);

  const handleCapture = async () => {
    if (!camera.current || isTakingPhoto) return;
    try {
      setIsTakingPhoto(true);

      // ① 撮影
      const photo = await camera.current.takePhoto({ flash: 'auto' });

      // ② ストアに追加（pending状態）
      const receipt = receiptStore.addReceipt(photo.path);
      setCapturedCount(prev => prev + 1);

      // ③ バックグラウンドでアップロード＋解析
      uploadAndAnalyze(receipt.id, photo.path);

    } catch (error) {
      console.error('撮影エラー:', error);
    } finally {
      setIsTakingPhoto(false);
    }
  };

  const uploadAndAnalyze = async (localId: string, imagePath: string) => {
    try {
      // ステータスを「解析中」に更新
      receiptStore.updateReceipt(localId, { status: 'analyzing' });

      // ④ アップロード
      const formData = new FormData();
      formData.append('userId', USER_ID);
      formData.append('image', {
        uri: `file://${imagePath}`,
        type: 'image/jpeg',
        name: 'receipt.jpg',
      } as any);

      const uploadRes = await fetch(`${API_BASE_URL}/api/receipts/upload`, {
        method: 'POST',
        body: formData,
      });

      if (!uploadRes.ok) throw new Error(`Upload failed: ${uploadRes.status}`);
      const uploadData = await uploadRes.json();
      const serverId = uploadData.id;

      // ⑤ OCR解析
      const analyzeRes = await fetch(
        `${API_BASE_URL}/api/receipts/${serverId}/analyze`,
        { method: 'POST' }
      );

      if (!analyzeRes.ok) throw new Error(`Analyze failed: ${analyzeRes.status}`);
      const analyzeData = await analyzeRes.json();

      // ⑥ 結果をストアに反映
      receiptStore.updateReceipt(localId, {
        status: 'done',
        storeName: analyzeData.storeName ?? '取得失敗',
        date: analyzeData.receiptDate ?? null,
        amount: analyzeData.amount ?? null,
      });

    } catch (error) {
      console.error('解析エラー:', error);
      receiptStore.updateReceipt(localId, { status: 'error' });
    }
  };

  if (!hasPermission) {
    return (
      <View style={styles.centered}>
        <Text style={styles.message}>カメラの権限が必要です</Text>
        <TouchableOpacity style={styles.button} onPress={requestPermission}>
          <Text style={styles.buttonText}>権限を許可する</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (!device) {
    return (
      <View style={styles.centered}>
        <Text style={styles.message}>カメラが見つかりません</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Camera
        ref={camera}
        style={styles.camera}
        device={device}
        isActive={true}
        photo={true}
      />
      <View style={styles.guide} />
      <SafeAreaView style={styles.header}>
        <Text style={styles.headerText}>領収書を枠内に合わせて撮影</Text>
      </SafeAreaView>
      {capturedCount > 0 && (
        <TouchableOpacity style={styles.badge} onPress={onNavigateToReview}>
          <Text style={styles.badgeText}>📋 {capturedCount}枚 →</Text>
        </TouchableOpacity>
      )}
      <View style={styles.controls}>
        <TouchableOpacity
          style={[styles.captureButton, isTakingPhoto && styles.disabled]}
          onPress={handleCapture}
          disabled={isTakingPhoto}
        >
          {isTakingPhoto
            ? <ActivityIndicator color="#000" />
            : <View style={styles.captureButtonInner} />
          }
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#000' },
  camera: { flex: 1 },
  header: {
    position: 'absolute', top: 0, width: '100%',
    alignItems: 'center', paddingTop: 16,
  },
  headerText: {
    color: '#fff', fontSize: 14,
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 16, paddingVertical: 6, borderRadius: 20,
  },
  guide: {
    position: 'absolute', top: '20%', left: '5%',
    width: '90%', height: '55%',
    borderWidth: 2, borderColor: 'rgba(255,255,255,0.7)', borderRadius: 8,
  },
  badge: {
    position: 'absolute', top: 50, right: 20,
    backgroundColor: '#00C853',
    borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8,
  },
  badgeText: { color: '#fff', fontWeight: 'bold', fontSize: 15 },
  controls: {
    position: 'absolute', bottom: 40,
    width: '100%', alignItems: 'center',
  },
  captureButton: {
    width: 80, height: 80, borderRadius: 40,
    backgroundColor: 'rgba(255,255,255,0.3)',
    borderWidth: 4, borderColor: '#fff',
    justifyContent: 'center', alignItems: 'center',
  },
  disabled: { opacity: 0.5 },
  captureButtonInner: { width: 60, height: 60, borderRadius: 30, backgroundColor: '#fff' },
  message: { color: '#fff', fontSize: 16, marginBottom: 20 },
  button: { backgroundColor: '#00C853', paddingHorizontal: 24, paddingVertical: 12, borderRadius: 8 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
});