package com.bookwormbliss.app.epub

object MetadataRefreshReportStore {

    @Volatile
    var latest: EpubImporter.MetadataRefreshResult? = null
}
