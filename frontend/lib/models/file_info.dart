class FileInfo {
  final String id;
  final String name;
  final String section; // 区域: wait, read, template, result
  final bool isDirectory;
  final DateTime modified;
  final int size;

  FileInfo({
    required this.id,
    required this.name,
    required this.section,
    required this.isDirectory,
    required this.modified,
    required this.size,
  });

  factory FileInfo.fromJson(Map<String, dynamic> json) {
    return FileInfo(
      id: json['id'],
      name: json['name'],
      section: json['section'],
      isDirectory: json['isDirectory'] ?? false,
      modified: DateTime.parse(json['modified']),
      size: json['size']?.toInt() ?? 0,
    );
  }
}