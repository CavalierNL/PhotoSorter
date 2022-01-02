package nl.cavaliernl.photosorter;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetaDataExtractor {
    private static final Logger logger = LogManager.getLogger(MetaDataExtractor.class);

    private static final Pattern PATTERN_DATE_STRIPPED = Pattern.compile(".*([0-9]{8}_[0-9]{6}).*");
    private static final Pattern PATTERN_DATE_DASHED = Pattern.compile(".*([0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}).*");
    private static final Pattern PATTERN_DATE_DASHED_DOTTED = Pattern.compile(".*([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}\\.[0-9]{2}\\.[0-9]{2}).*");
    private static final Pattern PATTERN_DATE_TIMESTAMP = Pattern.compile(".*([0-9]{13}).*");

    private static final DateTimeFormatter FORMATTER_DATE_STRIPPED = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FORMATTER_DATE_DASHED = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final DateTimeFormatter FORMATTER_DATE_DASHED_DOTTED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");

    private MetaDataExtractor() {
        throw new IllegalStateException("This class should not be instantiated.");
    }

    public static LocalDateTime getCreationInstant(Path path) {
        String detectedBy;
        LocalDateTime creationDateTime;

        creationDateTime = getCreationInstantFromFilename(path);
        detectedBy = "NAME";
        if (creationDateTime == null) {
            creationDateTime = getCreationInstantFromMetadata(path);
            detectedBy = "META";
        }
        if (creationDateTime == null) {
            creationDateTime = getCreationInstantFromCreationTime(path);
            detectedBy = "CRTD";
        }
        if (creationDateTime == null) {
            creationDateTime = getCreationInstantFromLastModifiedTime(path);
            detectedBy = "LSTM";
        }
        if (creationDateTime == null) {
            logger.warn("Could not determine creation time for path: {}", path);
            creationDateTime = LocalDateTime.now();
            detectedBy = "UNKN";
        }
        if (!"NAME".equals(detectedBy)) {
            logger.info("{}\t{}\t{}", creationDateTime, detectedBy, path);
        } else {
            logger.trace("{}\t{}\t{}", creationDateTime, detectedBy, path);
        }
        return creationDateTime;
    }

    public static LocalDateTime getCreationInstantFromFilename(Path path) {
        String filename = path.getFileName().toString();

        LocalDateTime instant = checkForFormattedDateTime(filename, PATTERN_DATE_DASHED_DOTTED, FORMATTER_DATE_DASHED_DOTTED);
        if (instant != null) {
            return instant;
        }

        instant = checkForFormattedDateTime(filename, PATTERN_DATE_STRIPPED, FORMATTER_DATE_STRIPPED);
        if (instant != null) {
            return instant;
        }

        instant = checkForFormattedDateTime(filename, PATTERN_DATE_DASHED, FORMATTER_DATE_DASHED);
        if (instant != null) {
            return instant;
        }

        Matcher matcher = PATTERN_DATE_TIMESTAMP.matcher(filename);
        if (matcher.matches()) {
            return getLocalDateTimeCurrentTimezone(Instant.ofEpochMilli(Long.parseLong(matcher.group(1))));
        }

        return null;
    }

    private static LocalDateTime checkForFormattedDateTime(String value, Pattern datePattern, DateTimeFormatter dateFormatter) {
        Matcher matcher = datePattern.matcher(value);
        if (matcher.matches()) {
            try {
                return LocalDateTime.parse(matcher.group(1), dateFormatter);
            } catch (DateTimeParseException e) {
                // skip
            }
        }
        return null;
    }

    public static LocalDateTime getCreationInstantFromMetadata(Path path) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(path.toFile());
        } catch (ImageProcessingException | IOException e) {
            logger.error("Could not read image metadata for path: {}", path, e);
            return null;
        }
        ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (directory != null) {
            Date originalDate = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (originalDate != null) {
                return getLocalDateTimeCurrentTimezone(originalDate.toInstant());
            }
        }
        return null;
    }

    public static LocalDateTime getCreationInstantFromCreationTime(Path path) {
        FileTime creationTime;
        try {
            creationTime = (FileTime) Files.getAttribute(path, "creationTime");
        } catch (IOException e) {
            logger.error("Could not read creation time for path: {}", path);
            return null;
        }
        if (creationTime != null) {
            return getLocalDateTimeCurrentTimezone(creationTime.toInstant());
        }
        return null;
    }

    public static LocalDateTime getCreationInstantFromLastModifiedTime(Path path) {
        try {
            return getLocalDateTimeCurrentTimezone(Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            logger.error("Could not read last modified time for path: {}", path);
            return null;
        }
    }

    private static LocalDateTime getLocalDateTimeCurrentTimezone(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.ofHours(0));
    }
}
