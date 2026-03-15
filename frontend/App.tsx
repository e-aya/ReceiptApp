import React, { useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import AuthScreen, { AuthUser } from './src/screens/AuthScreen';
import CameraScreen from './src/screens/CameraScreen';
import ReviewScreen from './src/screens/ReviewScreen';
import EditScreen from './src/screens/EditScreen';
import { initGoogleSignIn } from './src/utils/googleDrive';

type Screen = 'auth' | 'camera' | 'review' | 'edit';

export default function App() {
  const [screen, setScreen] = useState<Screen>('auth');
  const [user, setUser] = useState<AuthUser | null>(null);
  const [editReceiptId, setEditReceiptId] = useState<string | null>(null);

  useEffect(() => {
    initGoogleSignIn();
    // 保存済みトークンを確認
    AsyncStorage.getItem('authUser').then(json => {
      if (json) {
        setUser(JSON.parse(json));
        setScreen('camera');
      }
    });
  }, []);

  const handleAuthSuccess = async (authUser: AuthUser) => {
    console.log('Auth success:', authUser.userId, authUser.token?.substring(0, 20));
    await AsyncStorage.setItem('authUser', JSON.stringify(authUser));
    setUser(authUser);
    setScreen('camera');
  };

  const handleLogout = async () => {
    await AsyncStorage.removeItem('authUser');
    setUser(null);
    setScreen('auth');
  };

  if (screen === 'auth') {
    return <AuthScreen onAuthSuccess={handleAuthSuccess} />;
  }
  if (screen === 'edit' && editReceiptId) {
    return <EditScreen receiptId={editReceiptId} onBack={() => setScreen('review')} />;
  }
  if (screen === 'review') {
    return (
      <ReviewScreen
        onBack={() => setScreen('camera')}
        onEdit={id => { setEditReceiptId(id); setScreen('edit'); }}
        user={user}
        onLogout={handleLogout}
      />
    );
  }
  return (
    <CameraScreen
      onNavigateToReview={() => setScreen('review')}
      userId={user?.userId ?? 'test-user-001'}
      token={user?.token ?? ''}
    />
  );
}