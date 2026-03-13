import RNFS from 'react-native-fs';
import { Share } from 'react-native';
import { Receipt } from '../types';

export type CsvFormat = 'yayoi' | 'moneyforward' | 'freee';

const toYayoi = (receipts: Receipt[]): string => {
  const header = '\u7ba1\u7406\u756a\u53f7,\u53d6\u5f15\u65e5,\u501f\u65b9\u52d8\u5b9a\u79d1\u76ee,\u501f\u65b9\u88dc\u52a9\u79d1\u76ee,\u501f\u65b9\u7a0e\u533a\u5206,\u501f\u65b9\u91d1\u984d,\u8cb8\u65b9\u52d8\u5b9a\u79d1\u76ee,\u8cb8\u65b9\u88dc\u52a9\u79d1\u76ee,\u8cb8\u65b9\u7a0e\u533a\u5206,\u8cb8\u65b9\u91d1\u984d,\u6458\u8981';
  const rows = receipts.map((r, i) =>
    [i + 1, r.date ?? '', r.accountItem ?? '\u6d88\u8017\u54c1\u8cbb', '', '\u8ab2\u7a0e\u4ed810%', r.amount ?? 0, '\u73fe\u91d1', '', '', '', r.storeName ?? ''].join(',')
  );
  return [header, ...rows].join('\r\n');
};

const toMoneyForward = (receipts: Receipt[]): string => {
  const header = '\u53d6\u5f15\u65e5,\u53d6\u5f15\u5185\u5bb9,\u91d1\u984d\uff08\u5186\uff09,\u52d8\u5b9a\u79d1\u76ee,\u7a0e\u533a\u5206,\u30e1\u30e2,\u30bf\u30b0,\u6c7a\u6e08\u53e3\u5ea7';
  const rows = receipts.map(r =>
    [r.date ?? '', r.storeName ?? '', r.amount ?? 0, r.accountItem ?? '\u6d88\u8017\u54c1\u8cbb', '\u8ab2\u7a0e10%', '', '', '\u73fe\u91d1'].join(',')
  );
  return [header, ...rows].join('\r\n');
};

const toFreee = (receipts: Receipt[]): string => {
  const header = '\u767a\u751f\u65e5,\u52d8\u5b9a\u79d1\u76ee,\u53d6\u5f15\u5148,\u91d1\u984d,\u7a0e\u533a\u5206,\u5099\u8003,\u54c1\u76ee,\u90e8\u9580';
  const rows = receipts.map(r =>
    [r.date ?? '', r.accountItem ?? '\u6d88\u8017\u54c1\u8cbb', r.storeName ?? '', r.amount ?? 0, '\u8ab2\u7a0e\u4ed5\u5165(10%)', '', '', ''].join(',')
  );
  return [header, ...rows].join('\r\n');
};

export const exportCsv = async (receipts: Receipt[], format: CsvFormat): Promise<void> => {
  const done = receipts.filter(r => r.status === 'done');
  if (done.length === 0) throw new Error('\u89e3\u6790\u6e08\u307f\u306e\u9818\u53ce\u66f8\u304c\u3042\u308a\u307e\u305b\u3093');
  let csvContent = '';
  let fileName = '';
  switch (format) {
    case 'yayoi':
      csvContent = toYayoi(done);
      fileName = `\u9818\u53ce\u66f8_\u5f25\u751f_${today()}.csv`;
      break;
    case 'moneyforward':
      csvContent = toMoneyForward(done);
      fileName = `\u9818\u53ce\u66f8_MF_${today()}.csv`;
      break;
    case 'freee':
      csvContent = toFreee(done);
      fileName = `\u9818\u53ce\u66f8_freee_${today()}.csv`;
      break;
  }
  const bom = '\uFEFF';
  const filePath = `${RNFS.CachesDirectoryPath}/${fileName}`;
  await RNFS.writeFile(filePath, bom + csvContent, 'utf8');
  await Share.share({ title: fileName, url: `file://${filePath}`, message: filePath });
};

const today = (): string => {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
};