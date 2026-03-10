const DEV_API_URL = 'http://10.0.2.2:8080';   // Android エミュレータ
const PROD_API_URL = 'https://receiptapp-api-service-production.up.railway.app';

// __DEV__ はReact Nativeの組み込み変数（開発時true）
export const API_BASE_URL = __DEV__ ? DEV_API_URL : PROD_API_URL;