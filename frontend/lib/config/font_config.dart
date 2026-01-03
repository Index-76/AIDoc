import 'package:flutter/material.dart';

class FontConfig {
  // 定义字体大小常量
  static const double fontSizeExtraSmall = 10.0;
  static const double fontSizeSmall = 12.0;
  static const double fontSizeRegular = 14.0;
  static const double fontSizeMedium = 16.0;
  static const double fontSizeLarge = 18.0;
  static const double fontSizeExtraLarge = 20.0;
  static const double fontSizeTitle = 24.0;
  static const double fontSizeHeadline = 32.0;

  // 定义字体权重
  static const FontWeight fontWeightRegular = FontWeight.w400;
  static const FontWeight fontWeightMedium = FontWeight.w500;
  static const FontWeight fontWeightSemiBold = FontWeight.w600;
  static const FontWeight fontWeightBold = FontWeight.w700;

  // 创建应用主题的字体样式
  static ThemeData createTheme() {
    return ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      useMaterial3: true,
      fontFamily: 'Equilium', // 使用项目中的等线字体
      visualDensity: VisualDensity.adaptivePlatformDensity,
      textTheme: _buildTextTheme(),
    );
  }

  // 定义全局文本样式
  static TextTheme _buildTextTheme() {
    return const TextTheme(
      // 标题样式
      headlineLarge: TextStyle(
        fontSize: fontSizeHeadline,
        fontWeight: fontWeightBold,
        color: Colors.black87,
      ),
      headlineMedium: TextStyle(
        fontSize: fontSizeLarge,
        fontWeight: fontWeightSemiBold,
        color: Colors.black87,
      ),
      headlineSmall: TextStyle(
        fontSize: fontSizeMedium,
        fontWeight: fontWeightSemiBold,
        color: Colors.black87,
      ),
      // 标题样式（暗色主题）
      titleLarge: TextStyle(
        fontSize: fontSizeLarge,
        fontWeight: fontWeightMedium,
        color: Colors.white,
      ),
      
      // 普通文本样式
      bodyLarge: TextStyle(
        fontSize: fontSizeRegular,
        fontWeight: fontWeightRegular,
        color: Colors.black87,
      ),
      bodyMedium: TextStyle(
        fontSize: fontSizeSmall,
        fontWeight: fontWeightRegular,
        color: Colors.black87,
      ),
      bodySmall: TextStyle(
        fontSize: fontSizeExtraSmall,
        fontWeight: fontWeightRegular,
        color: Colors.black87,
      ),
      
      // 按钮文本样式
      labelLarge: TextStyle(
        fontSize: fontSizeMedium,
        fontWeight: fontWeightSemiBold,
        color: Colors.white,
      ),
    );
  }
}