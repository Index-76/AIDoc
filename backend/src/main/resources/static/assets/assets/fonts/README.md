# 字体文件

此目录用于存放项目所需的字体文件。目前项目配置了以下字体：

- `Equilium.ttf` - 等线字体

## 如何添加字体文件

1. 下载所需的字体文件（TTF格式）
2. 将字体文件复制到此目录
3. 确保文件名与 `pubspec.yaml` 中配置的文件名一致

## 字体来源

- 等线字体（Equilium）：可以从字体网站下载免费字体替代
- Droid Sans Fallback：Google开源字体，适用于显示各种语言字符

## 重新构建项目

添加字体文件后，请运行以下命令重新构建项目：

```bash
flutter clean
flutter pub get
flutter run
```