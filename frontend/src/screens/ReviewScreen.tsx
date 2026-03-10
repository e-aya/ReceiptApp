import React from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, Image, SafeAreaView, Alert,
} from 'react-native';
import { useReceipts, receiptStore } from '../store/receiptStore';
import { Receipt } from '../types';

interface Props {
  onBack: () => void;
}

export default function ReviewScreen({ onBack }: Props) {
  const receipts = useReceipts();

  const handleDelete = (id: string) => {
    Alert.alert('削除確認', 'この領収書を削除しますか？', [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: '削除', style: 'destructive',
        onPress: () => receiptStore.updateReceipt(id, { status: 'error' }),
      },
    ]);
  };

  const handleExportCSV = () => {
    const done = receipts.filter(r => r.status === 'done');
    if (done.length === 0) {
      Alert.alert('確認', 'OCR解析済みの領収書がありません\nPhase2実装後に利用可能です');
      return;
    }
    // TODO: Phase3でCSV出力実装
    Alert.alert('CSV出力', `${done.length}件を出力します（Phase3で実装）`);
  };

  const renderItem = ({ item }: { item: Receipt }) => (
    <View style={styles.item}>
      <Image source={{ uri: `file://${item.imagePath}` }} style={styles.thumbnail} />
      <View style={styles.itemInfo}>
        <Text style={styles.itemStatus}>
          {item.status === 'pending'   && '⏳ 解析待ち'}
          {item.status === 'analyzing' && '🔄 解析中...'}
          {item.status === 'done'      && '✅ 解析完了'}
          {item.status === 'error'     && '❌ エラー'}
        </Text>
        <Text style={styles.itemDetail}>
          🏪 {item.storeName ?? '未取得'}
        </Text>
        <Text style={styles.itemDetail}>
          📅 {item.date ?? '未取得'}
        </Text>
        <Text style={styles.itemDetail}>
          💴 {item.amount ? `¥${item.amount}` : '未取得'}
        </Text>
        <Text style={styles.itemTime}>
          {item.capturedAt.toLocaleTimeString('ja-JP')}
        </Text>
      </View>
      <TouchableOpacity
        style={styles.deleteButton}
        onPress={() => handleDelete(item.id)}
      >
        <Text style={styles.deleteText}>✕</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* ヘッダー */}
      <View style={styles.header}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backText}>← 撮影に戻る</Text>
        </TouchableOpacity>
        <Text style={styles.title}>撮影済み ({receipts.length}枚)</Text>
        <TouchableOpacity onPress={handleExportCSV} style={styles.csvButton}>
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
  deleteButton: { padding: 4 },
  deleteText: { color: '#999', fontSize: 18 },
  empty: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { color: '#999', fontSize: 16 },
});