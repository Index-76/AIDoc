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
}