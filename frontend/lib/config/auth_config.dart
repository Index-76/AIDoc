import 'package:shared_preferences/shared_preferences.dart';

class AuthConfig {
  static String? _userToken;
  static String? _username;
  static bool _isLoggedIn = false;

  // 初始化认证状态
  static Future<void> initialize() async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    
    _userToken = prefs.getString('user_token');
    _username = prefs.getString('username');
    _isLoggedIn = _userToken != null || _username != null;
  }

  // 检查用户是否已登录（同步方法）
  static bool isLoggedIn() {
    return _isLoggedIn;
  }

  // 设置登录状态
  static Future<void> setLoggedIn(String userToken, String username) async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    
    _userToken = userToken;
    _username = username;
    _isLoggedIn = true;
    
    await prefs.setString('user_token', userToken);
    await prefs.setString('username', username);
  }

  // 设置登出状态
  static Future<void> setLoggedOut() async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    
    _userToken = null;
    _username = null;
    _isLoggedIn = false;
    
    await prefs.remove('user_token');
    await prefs.remove('username');
    await prefs.remove('user_id');
  }

  // 清除认证信息
  static Future<void> clearAuthInfo() async {
    await setLoggedOut();
  }

  // 获取用户token
  static String? getUserToken() {
    return _userToken;
  }

  // 获取用户名
  static String? getUsername() {
    return _username;
  }
}