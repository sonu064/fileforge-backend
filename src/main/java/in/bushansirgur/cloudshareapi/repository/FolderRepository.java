package in.bushansirgur.cloudshareapi.repository;

import in.bushansirgur.cloudshareapi.document.FolderDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FolderRepository extends MongoRepository<FolderDocument, String> {

    List<FolderDocument> findByUserIdOrderByFolderNameAsc(String userId);

    List<FolderDocument> findByUserIdAndParentFolderIdOrderByFolderNameAsc(String userId, String parentFolderId);

    List<FolderDocument> findByParentFolderId(String parentFolderId);

    List<FolderDocument> findByUserIdAndFolderNameContainingIgnoreCase(String userId, String folderName);

    long countByUserIdAndParentFolderId(String userId, String parentFolderId);
}
