package in.bushansirgur.cloudshareapi.repository;

import in.bushansirgur.cloudshareapi.document.ShareLinkDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends MongoRepository<ShareLinkDocument, String> {

    Optional<ShareLinkDocument> findByShareId(String shareId);

    List<ShareLinkDocument> findByFileIdOrderByCreatedAtDesc(String fileId);

    List<ShareLinkDocument> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<ShareLinkDocument> findByActiveTrueAndExpiresAtBefore(Instant cutoff);

    void deleteByFileId(String fileId);
}
