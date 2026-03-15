import {
  GoogleSignin,
  statusCodes,
} from '@react-native-google-signin/google-signin';
import RNFS from 'react-native-fs';

// Google Sign-In 初期化
export const initGoogleSignIn = () => {
  GoogleSignin.configure({
    scopes: ['https://www.googleapis.com/auth/drive.file'],
    webClientId: '997108711378-rgk98fr3v3od7r37vkl64j288fhrcddq.apps.googleusercontent.com', // ★ Google Consoleで取得したWebクライアントID
  });
};

// サインイン（未サインインの場合のみ）
export const ensureSignedIn = async (): Promise<string> => {
  try {
    // 既にサインイン済みか確認
    const isSignedIn = await GoogleSignin.hasPreviousSignIn();
    if (!isSignedIn) {
      await GoogleSignin.signIn();
    }
    const tokens = await GoogleSignin.getTokens();
    return tokens.accessToken;
  } catch (error: any) {
    if (error.code === statusCodes.SIGN_IN_CANCELLED) {
      throw new Error('Googleサインインがキャンセルされました');
    }
    throw error;
  }
};

// フォルダ作成（存在する場合はIDを返す）
const findOrCreateFolder = async (
  accessToken: string,
  folderName: string,
  parentId: string | null = null
): Promise<string> => {
  // フォルダ検索
  const query = parentId
    ? `name='${folderName}' and mimeType='application/vnd.google-apps.folder' and '${parentId}' in parents and trashed=false`
    : `name='${folderName}' and mimeType='application/vnd.google-apps.folder' and trashed=false`;

  const searchRes = await fetch(
    `https://www.googleapis.com/drive/v3/files?q=${encodeURIComponent(query)}&fields=files(id,name)`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  const searchData = await searchRes.json();

  if (searchData.files?.length > 0) {
    return searchData.files[0].id;
  }

  // フォルダ作成
  const createBody: any = {
    name: folderName,
    mimeType: 'application/vnd.google-apps.folder',
  };
  if (parentId) createBody.parents = [parentId];

  const createRes = await fetch(
    'https://www.googleapis.com/drive/v3/files',
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(createBody),
    }
  );
  const createData = await createRes.json();
  return createData.id;
};

// 領収書をGoogleドライブに保存
export const uploadReceiptToDrive = async (
  imagePath: string,
  storeName: string | null,
  date: string | null,
  amount: number | null
): Promise<string> => {
  const accessToken = await ensureSignedIn();

  // フォルダ構成: 領収書/2026年/03月/
  const rootId   = await findOrCreateFolder(accessToken, '領収書');
  const year     = date ? date.substring(0, 4) : new Date().getFullYear().toString();
  const month    = date ? date.substring(5, 7) : String(new Date().getMonth() + 1).padStart(2, '0');
  const yearId   = await findOrCreateFolder(accessToken, `${year}年`, rootId);
  const monthId  = await findOrCreateFolder(accessToken, `${month}月`, yearId);

  // ファイル名生成
  const safeStoreName = (storeName ?? '不明').replace(/[/\\:*?"<>|]/g, '_');
  const safeDate      = (date ?? new Date().toISOString().substring(0, 10)).replace(/-/g, '');
  const safeAmount    = amount ? `${amount}円` : '不明';
  const fileName      = `${safeStoreName}_${safeDate}_${safeAmount}.jpg`;

  // 画像をBase64で読み込み
  const base64 = await RNFS.readFile(imagePath, 'base64');
//   const imageBytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0));

  // マルチパートアップロード
  const boundary = 'receipt_boundary_001';
  const metadata = JSON.stringify({
    name: fileName,
    parents: [monthId],
  });

  const multipartBody = [
    `--${boundary}`,
    'Content-Type: application/json; charset=UTF-8',
    '',
    metadata,
    `--${boundary}`,
    'Content-Type: image/jpeg',
    'Content-Transfer-Encoding: base64',
    '',
    base64,
    `--${boundary}--`,
  ].join('\r\n');

  const uploadRes = await fetch(
    'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink',
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': `multipart/related; boundary=${boundary}`,
      },
      body: multipartBody,
    }
  );

  const uploadData = await uploadRes.json();

  if (!uploadData.id) {
    throw new Error('Googleドライブへのアップロードに失敗しました');
  }

  console.log(`Drive保存完了: ${fileName} (${uploadData.id})`);
  return uploadData.id;
};