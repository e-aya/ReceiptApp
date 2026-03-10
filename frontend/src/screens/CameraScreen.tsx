import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  ActivityIndicator, SafeAreaView,
} from 'react-native';
import {
  Camera, useCameraDevice, useCameraPermission,
} from 'react-native-vision-camera';
import { receiptStore } from '../store/receiptStore';

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
      const photo = await camera.current.takePhoto({ flash: 'auto' });

      // ストアに追加
      receiptStore.addReceipt(photo.path);
      setCapturedCount(prev => prev + 1);

    } catch (error) {
      console.error('撮影エラー:', error);
    } finally {
      setIsTakingPhoto(false);
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

      {/* ガイド枠 */}
      <View style={styles.guide} />

      {/* ヘッダー */}
      <SafeAreaView style={styles.header}>
        <Text style={styles.headerText}>領収書を枠内に合わせて撮影</Text>
      </SafeAreaView>

      {/* 撮影済みバッジ → タップでレビュー画面へ */}
      {capturedCount > 0 && (
        <TouchableOpacity style={styles.badge} onPress={onNavigateToReview}>
          <Text style={styles.badgeText}>📋 {capturedCount}枚 →</Text>
        </TouchableOpacity>
      )}

      {/* 撮影ボタン */}
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