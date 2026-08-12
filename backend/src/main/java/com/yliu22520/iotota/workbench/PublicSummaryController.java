package com.yliu22520.iotota.workbench;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSummaryController {

    private final PublicSummaryCatalog catalog;

    public PublicSummaryController(PublicSummaryCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/diagnostic-summaries")
    public WorkbenchDtos.PublicSummaryList list() {
        return new WorkbenchDtos.PublicSummaryList(catalog.list());
    }
}
