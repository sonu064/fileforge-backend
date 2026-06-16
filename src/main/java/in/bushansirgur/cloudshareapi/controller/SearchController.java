package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.dto.SearchResultDTO;
import in.bushansirgur.cloudshareapi.service.FileMetadataService;
import in.bushansirgur.cloudshareapi.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final FileMetadataService fileMetadataService;
    private final FolderService folderService;

    @GetMapping
    public ResponseEntity<SearchResultDTO> search(@RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(SearchResultDTO.builder()
                .files(fileMetadataService.searchFiles(q))
                .folders(folderService.searchFolders(q))
                .build());
    }
}
