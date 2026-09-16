# Supplied web-app visual reference audit

The supplied source contains the following major UI surfaces/components used as the Android visual reference: HomeView, LibraryView, AuthorsAndSeriesViews, ReadingStatsView, SettingsView, SearchView, VocabularyView, BookDetailsModal, MetadataEditModal, ReadingNookView, ReaderView, ReaderBookmarksSheet, ReaderHighlightsSheet, ReaderSearchSheet, ReaderProgressSeeker, Drawer, Navbar, BookCard, and ImportModal.

The web source establishes a warm blush/sepia visual language. The primary values in `src/index.css` include accent `#D88C9A`, accent-dark `#C6707E`, secondary `#B48EAE`, warm light accent/border `#F1E3D3`, light background `#FFEEF2`, surface `#FFFFFF`, alternate surface `#FFE4F3`, and primary text `#5A4650`. Reader typography references Cinzel for headings, Literata/Georgia for serif reading, Plus Jakarta Sans for sans reading, and JetBrains Mono for monospace.

The Android v1 reproduces these visual tokens through Android resources rather than copying Tailwind classes. This is intentional: future theme additions can change centralized semantic resources/registries without editing every screen.

The supplied web reader was explicitly treated as a visual reference only. Its chapter rendering/state implementation is not used as the Android reader core. Android v1 establishes a separate EPUB pipeline and reader renderer boundary.
