import React, { useState } from 'react';
import CameraScreen from './src/screens/CameraScreen';
import ReviewScreen from './src/screens/ReviewScreen';

type Screen = 'camera' | 'review';

export default function App() {
  const [screen, setScreen] = useState<Screen>('camera');

  if (screen === 'review') {
    return <ReviewScreen onBack={() => setScreen('camera')} />;
  }
  return <CameraScreen onNavigateToReview={() => setScreen('review')} />;
}