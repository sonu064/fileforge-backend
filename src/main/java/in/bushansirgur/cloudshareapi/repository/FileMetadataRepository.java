package in.bushansirgur.cloudshareapi.repository;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FileMetadataRepository extends MongoRepository<FileMetadataDocument, String> {

    List<FileMetadataDocument> findByUserId(String userId);

    List<FileMetadataDocument> findByUserIdAndFolderId(String userId, String folderId);

    List<FileMetadataDocument> findByUserIdAndDeletedFalse(String userId);

    List<FileMetadataDocument> findByUserIdAndFolderIdAndDeletedFalse(String userId, String folderId);

    List<FileMetadataDocument> findByUserIdAndFavoriteTrueAndDeletedFalse(String userId);

    List<FileMetadataDocument> findByUserIdAndDeletedTrueOrderByDeletedAtDesc(String userId);

    List<FileMetadataDocument> findByUserIdAndFavoriteTrue(String userId);

    Long countByUserId(String userId);
}
