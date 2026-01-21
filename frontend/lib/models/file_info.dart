class FileInfo {
  final String id;
  final String fileName; // 后端返回的是fileName，不是name
  final String originalName; // 后端返回originalName
  final String contentType; // 后端返回contentType
  final int size; // 后端返回size
  final String section; // 后端返回section
  final int userId; // 后端返回userId
  final DateTime uploadTime; // 后端返回uploadTime
  final String filePath; // 后端返回filePath

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
      userId: json['userId']?.toInt() ?? 0,
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
