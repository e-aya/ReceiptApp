import { useState, useCallback } from 'react';
import { Receipt } from '../types';

// シンプルなグローバルストア（Zustand不要版）
let globalReceipts: Receipt[] = [];
let listeners: (() => void)[] = [];

const notifyListeners = () => listeners.forEach(l => l());

export const receiptStore = {
  getReceipts: () => globalReceipts,

  addReceipt: (imagePath: string) => {
    const newReceipt: Receipt = {
      id: Date.now().toString(),
      imagePath,
      capturedAt: new Date(),
      status: 'pending',
      storeName: null,
      date: null,
      amount: null,
    };
    globalReceipts = [...globalReceipts, newReceipt];
    notifyListeners();
    return newReceipt;
  },

  // 領収書を更新
  updateReceipt: (id: string, updates: Partial<Receipt>) => {
    globalReceipts = globalReceipts.map(r =>
      r.id === id ? { ...r, ...updates } : r
    );
    notifyListeners();
  },
  // 領収書を削除
  removeReceipt: (id: string) => {
    globalReceipts = globalReceipts.filter(r => r.id !== id);
    notifyListeners();
  },
  clearAll: () => {
    globalReceipts = [];
    notifyListeners();
  },
};

// React Hook
export const useReceipts = () => {
  const [receipts, setReceipts] = useState<Receipt[]>(globalReceipts);

  useCallback(() => {
    const listener = () => setReceipts([...globalReceipts]);
    listeners.push(listener);
    return () => {
      listeners = listeners.filter(l => l !== listener);
    };
  }, [])();

  return receipts;
};