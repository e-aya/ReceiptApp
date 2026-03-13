import RNFS from 'react-native-fs';
import { Share } from 'react-native';
import { Receipt } from '../types';

export type CsvFormat = 'yayoi' | 'moneyforward' | 'freee';

// ── 弥生会計 ──────────────────────────────
const toYayoi = (receipts: Receipt[]): string => {
  const header = '管理番号,取引日,借方勘定科目,借方補助科目,借方税区分,借方金額,貸方勘定科目,貸方補助科目,貸方税区分,貸方金額,摘要';
  const rows = receipts.map((r, i) =>
    [
      i + 1,
      r.date ?? '',
      r.accountItem ?? '消耗品費', // ★ 編集画面で設定した勘定科目を使用
      '', '課税仕入10%',
      r.amount ?? 0,
      '現金', '', '',
      '',
      r.storeName ?? '',
    ].join(',')
  );
  return [header, ...rows].join('\r\n');
};

// ── マネーフォワード ───────────────────────
const toMoneyForward = (receipts: Receipt[]): string => {
  const header = '取引日,取引内容,金額（円）,勘定科目,税区分,メモ,タグ,決済口座';
  const rows = receipts.map(r =>
    [
      r.date ?? '',
      r.storeName ?? '',
      r.amount ?? 0,
      r.accountItem ?? '消耗品費', // ★
      '課税10%',
      '', '', '現金',
    ].join(',')
  );
  return [header, ...rows].join('\r\n');
};

// ── freee ─────────────────────────────────
const toFreee = (receipts: Receipt[]): string => {
  const header = '発生日,勘定科目,取引先,金額,税区分,備考,品目,部門';
  const rows = receipts.map(r =>
    [
      r.date ?? '',
      r.accountItem ?? '消耗品費', // ★
      r.storeName ?? '',
      r.amount ?? 0,
      '課税仕入(10%)',
      '', '', '',
    ].join(',')
  );
  return [header, ...rows].join('\r\n');
};

// ── メイン関数 ────────────────────────────
export const exportCsv = async (
  receipts: Receipt[],
  format: CsvFormat
): Promise<void> => {
  const done = receipts.filter(r => r.status === 'done');
  if (done.length === 0) throw new Error('解析済みの領収書がありません');

  let csvContent = '';
  let fileName = '';

  switch (format) {
    case 'yayoi':
      csvContent = toYayoi(done);
      fileName = `領収書_弥生_${today()}.csv`;
      break;
    case 'moneyforward':
      csvContent = toMoneyForward(done);
      fileName = `領収書_MF_${today()}.csv`;
      break;
    case 'freee':
      csvContent = toFreee(done);
      fileName = `領収書_freee_${today()}.csv`;
      break;
  }

  const bom = '\uFEFF';
  const filePath = `${RNFS.CachesDirectoryPath}/${fileName}`;
  await RNFS.writeFile(filePath, bom + csvContent, 'utf8');

  await Share.share({
    title: fileName,
    url: `file://${filePath}`,
    message: filePath,
  });
};

const today = (): string => {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
};