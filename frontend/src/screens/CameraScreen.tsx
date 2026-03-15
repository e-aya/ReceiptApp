import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  ActivityIndicator, Alert,
} from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import {
  Camera, useCameraDevice, useCameraPermission,
} from 'react-native-vision-camera';
import ImageResizer from 'react-native-image-resizer';
import { receiptStore } from '../store/receiptStore';
import { initGoogleSignIn, uploadReceiptToDrive } from '../utils/googleDrive';

// ★ 本番APIのURL
const API_BASE_URL = 'https://receiptapp-api-service-production.up.railway.app';
//const API_BASE_URL = 'http://localhost:8080';
//const USER_ID = 'test-user-001';

interface Props {
  onNavigateToReview: () => void;
  onBack: () => void;      // ★ メインに戻る
  userId: string;
  token: string;
  planId?: string;
}

interface UsageInfo {
  usedCount: number;
  limit: number;
  remaining: number;
  planId: string;
}

export default function CameraScreen({ onNavigateToReview, onBack, userId, token, planId }: Props) {
  const { hasPermission, requestPermission } = useCameraPermission();
  const device = useCameraDevice('back');
  const camera = useRef<Camera>(null);
  const [isTakingPhoto, setIsTakingPhoto] = useState(false);
  const [capturedCount, setCapturedCount] = useState(0);
  const [usage, setUsage] = useState<UsageInfo | null>(null);

  useEffect(() => {
    if (!hasPermission) requestPermission();
    initGoogleSignIn(); // ★ 追加
    fetchUsage();
  }, [hasPermission]);

  const fetchUsage = useCallback(async () => {
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/receipts/usage?userId=${userId}`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      if (res.ok) {
        const data = await res.json();
        setUsage(data);
      }
    } catch (e) {
      console.warn('使用量取得失敗:', e);
    }
  }, [userId, token]);

  // ★ 最適化関数
  const optimizeImage = async (imagePath: string): Promise<string> => {
    try {
      const resized = await ImageResizer.createResizedImage(
        imagePath,
        1024,
        1024,
        'JPEG',  // ★ HEIC/PNG問わずJPEGに変換
        80,
        0,
      );
      return resized.path;
    } catch (e) {
      console.warn('リサイズ失敗、元画像使用:', e);
      return imagePath;
    }
  };

  const handleCapture = async () => {
    if (!camera.current || isTakingPhoto) return;

    // 残枚数チェック
    if (usage && usage.remaining <= 0) {
      Alert.alert(
        '今月の上限に達しました',
        `無料プランは月${usage.limit}枚まで撮影できます\n有料プランにアップグレードしてください`,
        [{ text: 'OK' }]
      );
      return;
    }

    try {
      setIsTakingPhoto(true);

      // ① 撮影
      const photo = await camera.current.takePhoto({ flash: 'auto' });

      // handleCapture内の uploadAndAnalyze 呼び出し前に追加
      const optimizedPath = await optimizeImage(photo.path);

      // ② ストアに追加（pending状態）
      const receipt = receiptStore.addReceipt(photo.path);
      setCapturedCount(prev => prev + 1);

      // 残枚数を即時更新（楽観的更新）
      if (usage) {
        setUsage(prev => prev ? {
          ...prev,
          usedCount: prev.usedCount + 1,
          remaining: Math.max(0, prev.remaining - 1),
        } : null);
      }

      // ③ バックグラウンドでアップロード＋解析
      uploadAndAnalyze(receipt.id, optimizedPath);

    } catch (error) {
      console.error('撮影エラー:', error);
    } finally {
      setIsTakingPhoto(false);
    }
  };

  const uploadAndAnalyze = async (localId: string, imagePath: string) => {
    try {
      console.log('=== DEBUG ===');
      console.log('userId:', userId);
      console.log('token length:', token?.length ?? 0);
      console.log('token preview:', token ? token.substring(0, 30) : 'EMPTY');
      // ステータスを「解析中」に更新
      receiptStore.updateReceipt(localId, { status: 'analyzing' });

      // ④ アップロード
      const formData = new FormData();
      formData.append('userId', userId);
      formData.append('image', {
        uri: `file://${imagePath}`,
        type: 'image/jpeg', // ★ 常にJPEGとして送信
        name: 'receipt.jpg',
      } as any);

      const uploadRes = await fetch(`${API_BASE_URL}/api/receipts/upload`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`, // ★認証トークン追加
          'Content-Type': 'application/json',
        },
        body: formData,
      });

      if (!uploadRes.ok) throw new Error(`Upload failed: ${uploadRes.status}`);
      const uploadData = await uploadRes.json();
      const serverId = uploadData.id;

      // ⑤ OCR解析
      const analyzeRes = await fetch(
        `${API_BASE_URL}/api/receipts/${serverId}/analyze`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`, // ★認証トークン追加
            'Content-Type': 'application/json',
          },
        }
      );

      if (!analyzeRes.ok) throw new Error(`Analyze failed: ${analyzeRes.status}`);
      const analyzeData = await analyzeRes.json();

      // ⑥ 結果をストアに反映
      receiptStore.updateReceipt(localId, {
        status: 'done',
        storeName: analyzeData.storeName ?? '取得失敗',
        date: analyzeData.receiptDate ?? null,
        amount: analyzeData.amount ?? null,
        accountItem: analyzeData.accountItem ?? null,
      });

      // ★ Googleドライブに保存（バックグラウンド）
      uploadReceiptToDrive(
        imagePath,
        analyzeData.storeName,
        analyzeData.receiptDate,
        analyzeData.amount
      ).catch(e => console.warn('Drive保存失敗:', e));

    } catch (error) {
      console.error('解析エラー:', error);
      receiptStore.updateReceipt(localId, { status: 'error' });
    }
  };

  // 残枚数バーの色
  const getUsageColor = () => {
    if (!usage) return '#fff';
    const ratio = usage.remaining / usage.limit;
    if (ratio > 0.5) return '#00C853';
    if (ratio > 0.2) return '#FFC107';
    return '#F44336';
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

      {/* ★ 上部ヘッダー：戻るボタン + 使用量 */}
      <SafeAreaProvider style={styles.topBar}>
        {/* 戻るボタン */}
        <TouchableOpacity style={styles.backButton} onPress={onBack}>
          <Text style={styles.backText}>✕</Text>
        </TouchableOpacity>

        {/* 使用量表示 */}
        {usage && (
          <View style={styles.usageBadge}>
            <Text style={[styles.usageText, { color: getUsageColor() }]}>
              今月残り
              <Text style={styles.usageCount}> {usage.remaining} </Text>
              / {usage.limit}枚
            </Text>
          </View>
        )}

        {/* 撮影済みバッジ */}
        {capturedCount > 0 && (
          <TouchableOpacity style={styles.badge} onPress={onNavigateToReview}>
            <Text style={styles.badgeText}>📋 {capturedCount}枚 →</Text>
          </TouchableOpacity>
        )}
      </SafeAreaProvider>

      {/* ガイドテキスト */}
      <View style={styles.guideTextContainer}>
        <Text style={styles.guideText}>領収書を枠内に合わせて撮影</Text>
      </View>

      {/* 撮影ボタン */}
      <View style={styles.controls}>
        <TouchableOpacity
          style={[
            styles.captureButton,
            (isTakingPhoto || (usage?.remaining ?? 1) <= 0) && styles.disabled,
          ]}
          onPress={handleCapture}
          disabled={isTakingPhoto || (usage?.remaining ?? 1) <= 0}
        >
          {isTakingPhoto
            ? <ActivityIndicator color="#000" />
            : <View style={styles.captureButtonInner} />
          }
        </TouchableOpacity>
        {usage?.remaining === 0 && (
          <Text style={styles.limitText}>今月の上限に達しました</Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  centered: {
    flex: 1, justifyContent: 'center',
    alignItems: 'center', backgroundColor: '#000',
  },
  camera: { flex: 1 },

  // ★ 上部バー
  topBar: {
    position: 'absolute', top: 0, width: '100%',
    flexDirection: 'row', alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16, paddingTop: 8,
  },
  backButton: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center', alignItems: 'center',
  },
  backText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },

  // ★ 使用量バッジ
  usageBadge: {
    backgroundColor: 'rgba(0,0,0,0.6)',
    borderRadius: 16, paddingHorizontal: 14, paddingVertical: 6,
  },
  usageText: { fontSize: 13 },
  usageCount: { fontSize: 18, fontWeight: 'bold' },

  badge: {
    backgroundColor: '#00C853',
    borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8,
  },
  badgeText: { color: '#fff', fontWeight: 'bold', fontSize: 15 },

  // ガイド枠
  guide: {
    position: 'absolute', top: '20%', left: '5%',
    width: '90%', height: '55%',
    borderWidth: 2, borderColor: 'rgba(255,255,255,0.7)', borderRadius: 8,
  },
  guideTextContainer: {
    position: 'absolute', bottom: '18%',
    width: '100%', alignItems: 'center',
  },
  guideText: {
    color: '#fff', fontSize: 14,
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 16, paddingVertical: 6, borderRadius: 20,
  },

  // 撮影ボタン
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
  disabled: { opacity: 0.4 },
  captureButtonInner: {
    width: 60, height: 60, borderRadius: 30, backgroundColor: '#fff',
  },
  limitText: {
    color: '#F44336', marginTop: 12, fontSize: 13,
    backgroundColor: 'rgba(0,0,0,0.6)',
    paddingHorizontal: 16, paddingVertical: 6, borderRadius: 20,
  },
  message: { color: '#fff', fontSize: 16, marginBottom: 20 },
  button: {
    backgroundColor: '#00C853',
    paddingHorizontal: 24, paddingVertical: 12, borderRadius: 8,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
});