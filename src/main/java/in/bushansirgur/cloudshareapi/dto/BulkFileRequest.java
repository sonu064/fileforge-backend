package in.bushansirgur.cloudshareapi.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkFileRequest {
    private List<String> fileIds;
}
