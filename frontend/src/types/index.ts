export interface Receipt {
  id: string;
  imagePath: string;
  capturedAt: Date;
  status: 'pending' | 'analyzing' | 'done' | 'error';
  // OCR結果（Phase2で埋まる）
  storeName: string | null;
  date: string | null;
  amount: string | null;
}