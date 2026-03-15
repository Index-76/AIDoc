class FileInfo {
  final String id;
  final String fileName;
  final String originalName;
  final String contentType;
  final int size;
  final String section;
  final String userId;
  final DateTime uploadTime;
  final String filePath;

  FileInfo({
    required this.id,
    required this.fileName,
    required this.originalName,
    required this.contentType,
    required this.size,
    required this.section,
    required this.userId,
    required this.uploadTime,
    required this.filePath,
  });

  factory FileInfo.fromJson(Map<String, dynamic> json) {
    return FileInfo(
      id: json['id'] ?? '',
      fileName: json['fileName'] ?? json['name'] ?? '',
      originalName: json['originalName'] ?? '',
      contentType: json['contentType'] ?? 'application/octet-stream',
      size: json['size']?.toInt() ?? 0,
      section: json['section'] ?? 'read',
      userId: json['userId']?.toString() ?? '',
      uploadTime: _parseUploadTime(json['uploadTime']),
      filePath: json['filePath'] ?? '',
    );
  }

  // 安全解析uploadTime，符合规范要求
  static DateTime _parseUploadTime(dynamic uploadTimeValue) {
    if (uploadTimeValue == null) {
      return DateTime.now();
    }

    try {
      if (uploadTimeValue is String) {
        return DateTime.parse(uploadTimeValue);
      } else if (uploadTimeValue is DateTime) {
        return uploadTimeValue;
      }
    } catch (e) {
      // 记录错误日志，但不抛出异常，而是返回当前时间
    }

    return DateTime.now();
  }
}
