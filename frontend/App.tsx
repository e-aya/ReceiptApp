import React, { useState } from 'react';
import CameraScreen from './src/screens/CameraScreen';
import EditScreen from './src/screens/EditScreen';
import ReviewScreen from './src/screens/ReviewScreen';

type Screen = 'camera' | 'review' | 'edit';

export default function App() {
  const [screen, setScreen] = useState<Screen>('camera');
  const [editReceiptId, setEditReceiptId] = useState<string | null>(null);

  const handleEdit = (id: string) => {
    setEditReceiptId(id);
    setScreen('edit');
  };

  if (screen === 'edit' && editReceiptId) {
    return (
      <EditScreen
        receiptId={editReceiptId}
        onBack={() => setScreen('review')}
      />
    );
  }
  if (screen === 'review') {
    return (
      <ReviewScreen
        onBack={() => setScreen('camera')}
        onEdit={handleEdit}
      />
    );
  }
  return <CameraScreen onNavigateToReview={() => setScreen('review')} />;
}