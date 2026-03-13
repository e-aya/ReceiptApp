export interface Receipt {
  id: string;
  imagePath: string;
  capturedAt: Date;
  status: 'pending' | 'analyzing' | 'done' | 'error';
  storeName: string | null;
  date: string | null;
  amount: number | null;       // ★ string → number
  accountItem: string | null;  // ★ 勘定科目 追加
}