import React, { useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, Image, SafeAreaView, Alert, Modal,
} from 'react-native';
import { useReceipts, receiptStore } from '../store/receiptStore';
import { Receipt } from '../types';
import { exportCsv, CsvFormat } from '../utils/csvExport';

// Propsに onEdit を追加
interface Props {
  onBack: () => void;
  onEdit: (id: string) => void;  // ★ 追加
}

export default function ReviewScreen({ onBack, onEdit }: Props) {
  const receipts = useReceipts();
  const [showFormatModal, setShowFormatModal] = useState(false);

  const handleDelete = (id: string) => {
    Alert.alert('削除確認', 'この領収書を削除しますか？', [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: '削除', style: 'destructive',
        onPress: () => receiptStore.removeReceipt(id),
      },
    ]);
  };

  const handleSelectFormat = async (format: CsvFormat) => {
    setShowFormatModal(false);
    try {
      await exportCsv(receipts, format);
    } catch (e: any) {
      Alert.alert('エラー', e.message);
    }
  };

  const doneCount = receipts.filter(r => r.status === 'done').length;

  const renderItem = ({ item }: { item: Receipt }) => (
    <View style={styles.item}>
      <Image source={{ uri: `file://${item.imagePath}` }} style={styles.thumbnail} />
      <View style={styles.itemInfo}>
        <Text style={styles.itemStatus}>
          {item.status === 'pending' && '⏳ 解析待ち'}
          {item.status === 'analyzing' && '🔄 解析中...'}
          {item.status === 'done' && '✅ 解析完了'}
          {item.status === 'error' && '❌ エラー'}
        </Text>
        <Text style={styles.itemDetail}>🏪 {item.storeName ?? '未取得'}</Text>
        <Text style={styles.itemDetail}>📅 {item.date ?? '未取得'}</Text>
        <Text style={styles.itemDetail}>
          💴 {item.amount ? `¥${item.amount}` : '未取得'}
        </Text>
        {/* ★ 勘定科目表示 */}
        <Text style={styles.itemDetail}>
          📒 {item.accountItem ?? '勘定科目未設定'}
        </Text>
        <Text style={styles.itemTime}>
          {item.capturedAt.toLocaleTimeString('ja-JP')}
        </Text>
      </View>
      <View style={styles.itemActions}>
        {/* ★ 編集ボタン（解析完了時のみ） */}
        {item.status === 'done' && (
          <TouchableOpacity
            style={styles.editButton}
            onPress={() => onEdit(item.id)}
          >
            <Text style={styles.editText}>✏️</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity
          style={styles.deleteButton}
          onPress={() => handleDelete(item.id)}
        >
          <Text style={styles.deleteText}>🗑</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* ヘッダー */}
      <View style={styles.header}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backText}>← 撮影に戻る</Text>
        </TouchableOpacity>
        <Text style={styles.title}>領収書済み ({receipts.length}枚)</Text>
        <TouchableOpacity
          style={[styles.csvButton, doneCount === 0 && styles.csvDisabled]}
          onPress={() => doneCount > 0 && setShowFormatModal(true)}
        >
          <Text style={styles.csvText}>CSV出力</Text>
        </TouchableOpacity>
      </View>

      {/* リスト */}
      {receipts.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>撮影した領収書がありません</Text>
        </View>
      ) : (
        <FlatList
          data={receipts}
          keyExtractor={item => item.id}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
        />
      )}

      {/* フォーマット選択モーダル */}
      <Modal visible={showFormatModal} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalBox}>
            <Text style={styles.modalTitle}>
              出力形式を選択（{doneCount}件）
            </Text>
            {[
              { key: 'yayoi',         label: '弥生会計' },
              { key: 'moneyforward',  label: 'マネーフォワード' },
              { key: 'freee',         label: 'freee' },
            ].map(f => (
              <TouchableOpacity
                key={f.key}
                style={styles.formatButton}
                onPress={() => handleSelectFormat(f.key as CsvFormat)}
              >
                <Text style={styles.formatText}>{f.label}</Text>
              </TouchableOpacity>
            ))}
            <TouchableOpacity
              style={styles.cancelButton}
              onPress={() => setShowFormatModal(false)}
            >
              <Text style={styles.cancelText}>キャンセル</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  header: {
    flexDirection: 'row', alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#fff', paddingHorizontal: 16, paddingVertical: 12,
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backButton: { padding: 4 },
  backText: { color: '#00C853', fontSize: 15 },
  title: { fontSize: 16, fontWeight: 'bold' },
  csvButton: {
    backgroundColor: '#00C853',
    paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6,
  },
  csvDisabled: { backgroundColor: '#ccc' },
  csvText: { color: '#fff', fontWeight: 'bold', fontSize: 13 },
  list: { padding: 12 },
  item: {
    flexDirection: 'row', backgroundColor: '#fff',
    borderRadius: 8, marginBottom: 10, padding: 10,
    shadowColor: '#000', shadowOpacity: 0.05,
    shadowRadius: 4, elevation: 2,
  },
  thumbnail: { width: 70, height: 90, borderRadius: 4, backgroundColor: '#eee' },
  itemInfo: { flex: 1, paddingHorizontal: 10 },
  itemStatus: { fontSize: 13, fontWeight: 'bold', marginBottom: 4 },
  itemDetail: { fontSize: 13, color: '#333', marginBottom: 2 },
  itemTime: { fontSize: 11, color: '#999', marginTop: 4 },
  itemActions: { justifyContent: 'space-between', alignItems: 'center' },
  editButton: { padding: 4, marginBottom: 8 },
  editText: { fontSize: 18 },
  deleteButton: { padding: 4 },
  deleteText: { color: '#999', fontSize: 18 },
  empty: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { color: '#999', fontSize: 16 },
  // モーダル
  modalOverlay: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalBox: {
    backgroundColor: '#fff', borderTopLeftRadius: 16,
    borderTopRightRadius: 16, padding: 24,
  },
  modalTitle: {
    fontSize: 16, fontWeight: 'bold',
    textAlign: 'center', marginBottom: 20,
  },
  formatButton: {
    backgroundColor: '#f0fdf4', borderWidth: 1, borderColor: '#00C853',
    borderRadius: 8, padding: 16, marginBottom: 12, alignItems: 'center',
  },
  formatText: { color: '#00C853', fontWeight: 'bold', fontSize: 16 },
  cancelButton: { padding: 16, alignItems: 'center' },
  cancelText: { color: '#999', fontSize: 15 },
});