package com.bookwormbliss.app.core.epub

import java.io.File

/**
 * Parsed EPUB container — the META-INF/container.xml layer.
 *
 * The container is the outermost layer of an EPUB: it points to the
 * root OPF (Package Document) file. This is deliberately separated
 * from the package parser so each step has a single responsibility.
 *
 * @property file       The EPUB (.epub) zip file.
 * @property opfPath    The path to the root OPF file within the zip
 *                      (e.g. "OEBPS/content.opf").
 * @property opfDir     The directory containing the OPF (e.g. "OEBPS").
 */
data class EpubContainer(
    val file: File,
    val opfPath: String,
    val opfDir: String,
)
