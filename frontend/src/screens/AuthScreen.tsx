import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, SafeAreaView, Alert, ActivityIndicator,
} from 'react-native';
import { GoogleSignin } from '@react-native-google-signin/google-signin';

const API_BASE_URL = 'https://receiptapp-api-service-production.up.railway.app';

export interface AuthUser {
  token: string;
  userId: string;
  email: string;
  name: string;
  planId: string;
}

interface Props {
  onAuthSuccess: (user: AuthUser) => void;
}

export default function AuthScreen({ onAuthSuccess }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const handleEmailAuth = async () => {
    if (!email || !password) {
      Alert.alert('エラー', 'メールとパスワードを入力してください');
      return;
    }
    setLoading(true);
    try {
      const endpoint = mode === 'login' ? '/api/auth/login' : '/api/auth/register';
      const res = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, name }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? 'エラーが発生しました');
      onAuthSuccess(data);
    } catch (e: any) {
      Alert.alert('エラー', e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleAuth = async () => {
    setLoading(true);
    try {
      await GoogleSignin.hasPlayServices();
      const userInfo = await GoogleSignin.signIn();
      const res = await fetch(`${API_BASE_URL}/api/auth/google`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          googleId: userInfo.data?.user.id,
          email: userInfo.data?.user.email,
          name: userInfo.data?.user.name,
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? 'エラーが発生しました');
      onAuthSuccess(data);
    } catch (e: any) {
      Alert.alert('エラー', e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>領収書アプリ</Text>
      <Text style={styles.subtitle}>
        {mode === 'login' ? 'ログイン' : '新規登録'}
      </Text>

      {mode === 'register' && (
        <TextInput
          style={styles.input}
          placeholder="お名前"
          value={name}
          onChangeText={setName}
          placeholderTextColor="#aaa"
        />
      )}
      <TextInput
        style={styles.input}
        placeholder="メールアドレス"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
        placeholderTextColor="#aaa"
      />
      <TextInput
        style={styles.input}
        placeholder="パスワード"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        placeholderTextColor="#aaa"
      />

      <TouchableOpacity
        style={styles.primaryButton}
        onPress={handleEmailAuth}
        disabled={loading}
      >
        {loading
          ? <ActivityIndicator color="#fff" />
          : <Text style={styles.primaryText}>
            {mode === 'login' ? 'ログイン' : '登録する'}
          </Text>
        }
      </TouchableOpacity>

      <View style={styles.divider}>
        <View style={styles.dividerLine} />
        <Text style={styles.dividerText}>または</Text>
        <View style={styles.dividerLine} />
      </View>

      <TouchableOpacity
        style={styles.googleButton}
        onPress={handleGoogleAuth}
        disabled={loading}
      >
        <Text style={styles.googleText}>🔵 Googleでログイン</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.switchButton}
        onPress={() => setMode(mode === 'login' ? 'register' : 'login')}
      >
        <Text style={styles.switchText}>
          {mode === 'login'
            ? 'アカウントをお持ちでない方はこちら'
            : '既にアカウントをお持ちの方はこちら'}
        </Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1, backgroundColor: '#fff',
    paddingHorizontal: 32, justifyContent: 'center',
  },
  title: {
    fontSize: 28, fontWeight: 'bold',
    textAlign: 'center', marginBottom: 8, color: '#00C853',
  },
  subtitle: {
    fontSize: 18, textAlign: 'center',
    marginBottom: 32, color: '#333',
  },
  input: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8,
    paddingHorizontal: 16, paddingVertical: 12,
    fontSize: 16, marginBottom: 16, color: '#333',
  },
  primaryButton: {
    backgroundColor: '#00C853', borderRadius: 8,
    paddingVertical: 14, alignItems: 'center', marginBottom: 16,
  },
  primaryText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  divider: {
    flexDirection: 'row', alignItems: 'center', marginBottom: 16,
  },
  dividerLine: { flex: 1, height: 1, backgroundColor: '#eee' },
  dividerText: { marginHorizontal: 12, color: '#999', fontSize: 14 },
  googleButton: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8,
    paddingVertical: 14, alignItems: 'center', marginBottom: 24,
  },
  googleText: { fontSize: 16, color: '#333', fontWeight: 'bold' },
  switchButton: { alignItems: 'center' },
  switchText: { color: '#00C853', fontSize: 14 },
});