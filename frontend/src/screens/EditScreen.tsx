// ## 画面遷移フロー
// CameraScreen
//   ↓ 撮影済みバッジタップ
// ReviewScreen
//   ↓ ✏️ボタンタップ（解析完了時のみ）
// EditScreen
//   ↓ 保存
// ReviewScreen（更新済み）
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TextInput,
  TouchableOpacity, ScrollView,
  Alert, Image,
} from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { receiptStore } from '../store/receiptStore';
import DateTimePicker from '@react-native-community/datetimepicker';

// 勘定科目マスタ
const ACCOUNT_ITEMS = [
  '消耗品費',
  '会議費',
  '接待交際費',
  '旅費交通費',
  '通信費',
  '広告宣伝費',
  '福利厚生費',
  '水道光熱費',
  '地代家賃',
  '雑費',
];

interface Props {
  receiptId: string;
  onBack: () => void;
}

export default function EditScreen({ receiptId, onBack }: Props) {
  const receipt = receiptStore.getReceipts().find(r => r.id === receiptId);

  if (!receipt) {
    return (
      <SafeAreaProvider style={styles.container}>
        <Text>領収書が見つかりません</Text>
      </SafeAreaProvider>
    );
  }

  // ★ 日付をDateオブジェクトで管理
  const parseInitialDate = (dateStr: string): Date => {
    const d = new Date(dateStr);
    return isNaN(d.getTime()) ? new Date() : d;
  };
  const [storeName, setStoreName]   = useState(receipt.storeName ?? '');
  //const [date, setDate]             = useState(receipt.date ?? '');
  const [date, setDate]           = useState<Date>(parseInitialDate(receipt.date ?? ''));
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [amount, setAmount]         = useState(receipt.amount?.toString() ?? '');
  const [accountItem, setAccountItem] = useState(receipt.accountItem ?? '');
  const [memo, setMemo]             = useState('');

  // 日付をYYYY-MM-DD文字列に変換するヘルパー
  const formatDate = (d: Date): string =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  const handleSave = () => {
    if (!storeName.trim()) {
      Alert.alert('エラー', '店名を入力してください');
      return;
    }
    // if (!date.trim()) {
    //   Alert.alert('エラー', '日付を入力してください');
    //   return;
    // }
    const amountNum = parseInt(amount.replace(/,/g, ''), 10);
    if (isNaN(amountNum) || amountNum <= 0) {
      Alert.alert('エラー', '正しい金額を入力してください');
      return;
    }
    if (!accountItem) {
      Alert.alert('エラー', '勘定科目を選択してください');
      return;
    }

    receiptStore.updateReceipt(receiptId, {
      storeName: storeName.trim(),
      date: formatDate(date),  // ★ Date → 文字列変換
      amount: amountNum,
      accountItem,
    });

    Alert.alert('保存完了', '内容を保存しました', [
      { text: 'OK', onPress: onBack },
    ]);
  };

  return (
    <SafeAreaProvider style={styles.container}>
      {/* ヘッダー */}
      <View style={styles.header}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backText}>← 戻る</Text>
        </TouchableOpacity>
        <Text style={styles.title}>領収書を編集</Text>
        <TouchableOpacity onPress={handleSave} style={styles.saveButton}>
          <Text style={styles.saveText}>保存</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        {/* サムネイル */}
        <Image
          source={{ uri: `file://${receipt.imagePath}` }}
          style={styles.thumbnail}
          resizeMode="contain"
        />

        {/* 店名 */}
        <View style={styles.field}>
          <Text style={styles.label}>🏪 店名</Text>
          <TextInput
            style={styles.input}
            value={storeName}
            onChangeText={setStoreName}
            placeholder="店名を入力"
            placeholderTextColor="#bbb"
          />
        </View>

        {/* 日付 */}
        <View style={styles.field}>
          <Text style={styles.label}>📅 日付</Text>
          <TouchableOpacity
            style={styles.dateButton}
            onPress={() => setShowDatePicker(true)}
          >
            <Text style={styles.dateButtonText}>{formatDate(date)}</Text>
            <Text style={styles.dateButtonIcon}>📅</Text>
          </TouchableOpacity>
          {showDatePicker && (
            <DateTimePicker
              value={date}
              mode="date"
              display="default"
              locale="ja-JP"
              maximumDate={new Date()}
              onChange={(event, selectedDate) => {
                setShowDatePicker(false);
                if (selectedDate) setDate(selectedDate);
              }}
            />
          )}
        </View>

        {/* 金額 */}
        <View style={styles.field}>
          <Text style={styles.label}>💴 金額（円）</Text>
          <TextInput
            style={styles.input}
            value={amount}
            onChangeText={setAmount}
            placeholder="例: 1500"
            placeholderTextColor="#bbb"
            keyboardType="numeric"
          />
        </View>

        {/* 勘定科目 */}
        <View style={styles.field}>
          <Text style={styles.label}>📒 勘定科目</Text>
          <View style={styles.accountGrid}>
            {ACCOUNT_ITEMS.map(item => (
              <TouchableOpacity
                key={item}
                style={[
                  styles.accountChip,
                  accountItem === item && styles.accountChipSelected,
                ]}
                onPress={() => setAccountItem(item)}
              >
                <Text style={[
                  styles.accountChipText,
                  accountItem === item && styles.accountChipTextSelected,
                ]}>
                  {item}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* メモ */}
        <View style={styles.field}>
          <Text style={styles.label}>📝 メモ（任意）</Text>
          <TextInput
            style={[styles.input, styles.memoInput]}
            value={memo}
            onChangeText={setMemo}
            placeholder="備考など"
            placeholderTextColor="#bbb"
            multiline
          />
        </View>
      </ScrollView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  header: {
    flexDirection: 'row', alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#fff',
    paddingHorizontal: 16, paddingVertical: 12,
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backButton: { padding: 4 },
  backText: { color: '#00C853', fontSize: 15 },
  title: { fontSize: 16, fontWeight: 'bold' },
  saveButton: {
    backgroundColor: '#00C853',
    paddingHorizontal: 16, paddingVertical: 6, borderRadius: 6,
  },
  saveText: { color: '#fff', fontWeight: 'bold', fontSize: 14 },
  scroll: { padding: 16 },
  thumbnail: {
    width: '100%', height: 200,
    borderRadius: 8, marginBottom: 20,
    backgroundColor: '#eee',
  },
  field: { marginBottom: 20 },
  label: { fontSize: 14, fontWeight: 'bold', color: '#333', marginBottom: 8 },
  input: {
    backgroundColor: '#fff', borderWidth: 1, borderColor: '#ddd',
    borderRadius: 8, paddingHorizontal: 14, paddingVertical: 10,
    fontSize: 16, color: '#333',
  },
  memoInput: { height: 80, textAlignVertical: 'top' },
  accountGrid: {
    flexDirection: 'row', flexWrap: 'wrap', gap: 8,
  },
  accountChip: {
    borderWidth: 1, borderColor: '#ddd',
    borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8,
    backgroundColor: '#fff',
  },
  accountChipSelected: {
    backgroundColor: '#00C853', borderColor: '#00C853',
  },
  accountChipText: { fontSize: 13, color: '#555' },
  accountChipTextSelected: { color: '#fff', fontWeight: 'bold' },
  dateButton: {
    backgroundColor: '#fff', borderWidth: 1, borderColor: '#ddd',
    borderRadius: 8, paddingHorizontal: 14, paddingVertical: 12,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
  },
  dateButtonText: { fontSize: 16, color: '#333' },
  dateButtonIcon: { fontSize: 16 },
});