package com.teacher.backend.dto;

import java.util.List;

public record BulkUserImportRequest(Long userId, String role, List<BulkUserRow> rows) {
}
