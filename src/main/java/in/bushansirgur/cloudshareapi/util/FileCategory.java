package in.bushansirgur.cloudshareapi.util;

import java.util.Set;

/** Classifies a file into a high-level category for type filtering. */
public final class FileCategory {

    public static final String IMAGE = "image";
    public static final String PDF = "pdf";
    public static final String DOCUMENT = "document";
    public static final String VIDEO = "video";
    public static final String AUDIO = "audio";
    public static final String ARCHIVE = "archive";
    public static final String EXECUTABLE = "executable";
    public static final String OTHER = "other";

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "tiff");
    private static final Set<String> DOC_EXT = Set.of("doc", "docx", "txt", "md", "csv", "rtf", "odt",
            "ppt", "pptx", "xls", "xlsx", "pages", "key", "numbers");
    private static final Set<String> ARCHIVE_EXT = Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz");
    private static final Set<String> EXE_EXT = Set.of("exe", "msi", "dmg", "deb", "rpm", "apk", "bat", "sh", "bin");

    private FileCategory() {}

    public static String classify(String name, String type) {
        String t = type == null ? "" : type.toLowerCase();
        String ext = extension(name);

        if (t.startsWith("image/") || IMAGE_EXT.contains(ext)) return IMAGE;
        if (t.equals("application/pdf") || ext.equals("pdf")) return PDF;
        if (t.startsWith("video/")) return VIDEO;
        if (t.startsWith("audio/")) return AUDIO;
        if (ARCHIVE_EXT.contains(ext) || t.contains("zip") || t.contains("compressed")) return ARCHIVE;
        if (EXE_EXT.contains(ext)) return EXECUTABLE;
        if (t.startsWith("text/") || t.contains("word") || t.contains("officedocument")
                || t.contains("spreadsheet") || t.contains("presentation") || DOC_EXT.contains(ext)) return DOCUMENT;
        return OTHER;
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
