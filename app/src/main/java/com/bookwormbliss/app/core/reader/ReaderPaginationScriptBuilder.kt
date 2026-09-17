package com.bookwormbliss.app.core.reader

/**
 * Extracted from ReaderActivity.paginationJs().
 *
 * Builds the pagination JavaScript injected into the reader WebView.
 *
 * Key fix for the "next-page sliver": columns are `column-gap = 2*margin`
 * wide and each page advances by the full viewport width (innerWidth), so
 * column N+1 begins exactly at the right edge of the viewport and never
 * peeks into page N. Bottom guard shrinks body height to reserve space for
 * the page indicator.
 *
 * This is a pure function of [guardPx] and [topGuardPx] — no Android
 * Context or Activity dependency.
 */
object ReaderPaginationScriptBuilder {

    fun build(guardPx: Int, topGuardPx: Int): String {
        return """

        <script>
        (function () {
          var body = null;
          var html = document.documentElement;
          var GUARD = $guardPx;
          var TOP_GUARD = $topGuardPx;

          function px(v) {
            return parseFloat(v) || 0;
          }

          function viewportW() {
            return window.innerWidth || html.clientWidth || 1;
          }

          function padX() {
            var cs = getComputedStyle(body);
            return px(cs.paddingLeft) + px(cs.paddingRight);
          }

          function gapW() {
            var cs = getComputedStyle(body);
            return px(cs.columnGap) || 0;
          }

          function colW() {
            return Math.max(1, viewportW() - padX());
          }

          function advance() {
            return colW() + gapW();
          }

          // Selection must never expose Chromium's horizontal column auto-scroll.
          // Keep the selection gesture on the page where it started while leaving
          // normal text selection, including vertical movement within that page,
          // untouched. This only constrains body.scrollLeft during an active
          // selection; normal pagination remains unchanged.
          var selectionPageLock = -1;
          var selectionPageLockActive = false;
          var selectionPageLockFrame = 0;

          function setSelectionPageLock(active, page) {
            selectionPageLockActive = !!active;
            if (!selectionPageLockActive) {
              selectionPageLock = -1;
              if (selectionPageLockFrame) {
                cancelAnimationFrame(selectionPageLockFrame);
                selectionPageLockFrame = 0;
              }
              return true;
            }

            if (typeof page === 'number' && isFinite(page)) {
              selectionPageLock = Math.max(0, Math.floor(page));
            } else if (selectionPageLock < 0) {
              selectionPageLock = currentPage();
            }

            function enforceSelectionPage() {
              if (!selectionPageLockActive || !body) {
                selectionPageLockFrame = 0;
                return;
              }
              var target = Math.max(0, selectionPageLock) * advance();
              if (Math.abs((body.scrollLeft || 0) - target) > 1) {
                body.scrollLeft = target;
              }
              selectionPageLockFrame = requestAnimationFrame(enforceSelectionPage);
            }

            if (!selectionPageLockFrame) {
              selectionPageLockFrame = requestAnimationFrame(enforceSelectionPage);
            }
            return true;
          }

          function pageCount() {
            if (!body) return 1;
            var scrollWidth = body.scrollWidth || body.offsetWidth || 1;
            return Math.max(1, Math.round(scrollWidth / advance()));
          }

          function currentPage() {
            if (!body) return 0;
            return Math.max(0, Math.round(body.scrollLeft / advance()));
          }

          function animateTo(target, duration) {
            if (!body) return;

            var start = body.scrollLeft || 0;
            var delta = target - start;
            if (delta === 0) return;

            var startedAt = performance.now();

            function ease(t) {
              return t < 0.5
                ? 2 * t * t
                : 1 - Math.pow(-2 * t + 2, 2) / 2;
            }

            function frame(now) {
              var progress = Math.min(1, (now - startedAt) / duration);
              body.scrollLeft = start + delta * ease(progress);

              if (progress < 1) {
                requestAnimationFrame(frame);
              }
            }

            requestAnimationFrame(frame);
          }

          function gotoPage(page, animate) {
            if (!body) return 0;

            var count = pageCount();
            var safePage = Math.max(
              0,
              Math.min(Math.floor(page), count - 1)
            );

            var target = safePage * advance();

            if (animate) {
              animateTo(target, 240);
            } else {
              body.scrollLeft = target;
            }

            return safePage;
          }

          function nextPage(animate) {
            var count = pageCount();
            var page = currentPage();

            if (page >= count - 1) return 'next-chapter';

            gotoPage(page + 1, animate);
            return 'ok';
          }

          function prevPage(animate) {
            var page = currentPage();

            if (page <= 0) return 'prev-chapter';

            gotoPage(page - 1, animate);
            return 'ok';
          }

          function ratio() {
            var count = pageCount();
            if (count <= 1) return 0;
            return currentPage() / (count - 1);
          }

          function pageForElementById(id) {
            var element = document.getElementById(id);

            if (!element) {
              var named = document.getElementsByName(id);
              if (named.length) element = named[0];
            }

            if (!element) return -1;

            var x = 0;
            var node = element;

            while (node) {
              x += node.offsetLeft || 0;
              node = node.offsetParent;
            }

            return Math.floor(x / advance());
          }

          function gotoElementById(id) {
            var page = pageForElementById(id);
            if (page < 0) return false;
            gotoPage(page, false);
            return true;
          }

          function highlightPageById(id) {
            var marks = document.querySelectorAll('mark.livre-highlight[data-highlight-id="' + id + '"]');
            if (!marks.length) return -1;
            var element = marks[0];
            var x = 0;
            var node = element;
            while (node) {
              x += node.offsetLeft || 0;
              node = node.offsetParent;
            }
            return Math.floor(x / advance());
          }

          function gotoHighlightById(id) {
            var page = highlightPageById(id);
            if (page < 0) return -1;
            gotoPage(page, false);
            return page;
          }

          function apply() {
            if (!body) return;

            var viewportHeight = window.innerHeight || html.clientHeight || 1;
            // Patch 12: reserve TOP_GUARD px at the top (breathing room below the
            // status bar) AND GUARD px at the bottom (page-indicator space). The
            // body is pushed down by TOP_GUARD and its column height shrinks by
            // (TOP_GUARD + GUARD) so the top and bottom reserved spaces are equal
            // and symmetric. Both spaces are painted with the reading background
            // color (white/sepia/black), not page content.
            var height = Math.max(40, viewportHeight - TOP_GUARD - GUARD);

            // html spans 0..(viewportHeight - GUARD): the top TOP_GUARD of it is
            // empty (html background = reading bg), the body sits below it.
            html.style.setProperty('height', (height + TOP_GUARD) + 'px', 'important');
            // Push the column container down by TOP_GUARD so page content starts
            // below the status bar. !important is needed because the body CSS sets
            // `margin:0 !important`.
            body.style.setProperty('margin-top', TOP_GUARD + 'px', 'important');
            body.style.setProperty('height', height + 'px', 'important');

            body.style.setProperty('column-width', colW() + 'px', 'important');
            body.style.setProperty('-webkit-column-width', colW() + 'px', 'important');
            body.style.setProperty('column-count', 'auto', 'important');
            body.style.setProperty('-webkit-column-count', 'auto', 'important');
            body.style.setProperty('column-fill', 'auto', 'important');
            body.style.setProperty('-webkit-column-fill', 'auto', 'important');
          }

          function pageOfRangeStart(range) {
            try {
              var rect = range.getBoundingClientRect();
              if (!rect || (!rect.width && !rect.height)) {
                var rects = range.getClientRects();
                if (!rects.length) return -1;
                rect = rects[0];
              }
              var bodyRect = body.getBoundingClientRect();
              var absoluteX = (body.scrollLeft || 0) + rect.left - bodyRect.left;
              return Math.max(0, Math.floor(absoluteX / advance()));
            } catch (e) {
              return -1;
            }
          }

          function pageSnippet(page) {
            if (!body) return '';
            var target = Math.max(0, Math.floor(page || 0));
            var actual = currentPage();
            if (target !== actual) gotoPage(target, false);

            var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null);
            var nodes = [];
            var totalLength = 0;
            var node;
            while ((node = walker.nextNode())) {
              var text = node.textContent || '';
              if (!text) continue;
              nodes.push({ node: node, start: totalLength });
              totalLength += text.length;
            }
            if (!nodes.length || !totalLength) return '';

            function positionForIndex(index) {
              var safe = Math.max(0, Math.min(index, totalLength));
              var lo = 0, hi = nodes.length - 1;
              while (lo <= hi) {
                var mid = Math.floor((lo + hi) / 2);
                if (safe < nodes[mid].start) hi = mid - 1;
                else if (mid + 1 < nodes.length && safe >= nodes[mid + 1].start) lo = mid + 1;
                else return { node: nodes[mid].node, offset: safe - nodes[mid].start };
              }
              var last = nodes[nodes.length - 1];
              return { node: last.node, offset: (last.node.textContent || '').length };
            }

            function pageAtIndex(index) {
              var pos = positionForIndex(index);
              var range = document.createRange();
              try {
                range.setStart(pos.node, pos.offset);
                range.collapse(true);
                return pageOfRangeStart(range);
              } finally { range.detach(); }
            }

            var low = 0, high = totalLength, first = totalLength;
            while (low <= high) {
              var mid = Math.floor((low + high) / 2);
              if (pageAtIndex(mid) >= target) { first = mid; high = mid - 1; }
              else low = mid + 1;
            }
            if (first === totalLength) return '';

            var boundary = first;
            while (boundary < totalLength && pageAtIndex(boundary) < target) boundary++;
            if (boundary >= totalLength) return '';

            // If the pagination boundary is inside a word, move to its first
            // complete word rather than storing the tail of that word.
            function charAt(index) {
              if (index < 0 || index >= totalLength) return '';
              var p = positionForIndex(index);
              return (p.node.textContent || '').charAt(p.offset);
            }
            if (boundary > 0 && !/\s/.test(charAt(boundary - 1)) && !/\s/.test(charAt(boundary))) {
              while (boundary < totalLength && !/\s/.test(charAt(boundary))) boundary++;
              while (boundary < totalLength && /\s/.test(charAt(boundary))) boundary++;
            } else {
              while (boundary < totalLength && /\s/.test(charAt(boundary))) boundary++;
            }

            var pos = positionForIndex(boundary);
            var currentNodeIndex = 0;
            for (var ni = 0; ni < nodes.length; ni++) {
              if (nodes[ni].node === pos.node) { currentNodeIndex = ni; break; }
            }

            var text = '';
            var sentences = 0;
            var previousSpace = true;
            for (var n = currentNodeIndex; n < nodes.length && text.length < 1400; n++) {
              var source = nodes[n].node.textContent || '';
              var from = n === currentNodeIndex ? pos.offset : 0;
              for (var i = from; i < source.length && text.length < 1400; i++) {
                var ch = source.charAt(i);
                if (/\s/.test(ch)) {
                  if (!previousSpace && text.length) text += ' ';
                  previousSpace = true;
                  continue;
                }
                text += ch;
                previousSpace = false;
                if (/[.!?]/.test(ch) && (i + 1 >= source.length || /\s/.test(source.charAt(i + 1)))) {
                  sentences++;
                  if (sentences >= 2 && text.length >= 160) break;
                }
              }
              if (sentences >= 2 && text.length >= 160) break;
            }
            text = text.replace(/\s+/g, ' ').trim();
            if (!text) return '';

            // Compact internal anchor: enough beginning/end context to identify
            // the location, without requiring the entire passage to match.
            var bodyText = text.slice(0, 1000);
            var startWindow = bodyText.slice(0, 180);
            var endWindow = bodyText.slice(-180);
            return JSON.stringify({ v: 1, start: startWindow, text: bodyText, end: endWindow });
          }

          function pageForWholePageAnchor(anchor, fallbackPage) {
            if (!anchor || !body) return fallbackPage || 0;
            var data = null;
            try { data = JSON.parse(String(anchor)); } catch (e) { data = null; }
            if (!data || data.v !== 1 || !data.start) return pageForTextAnchor(anchor, fallbackPage);

            var wantedStart = String(data.start).replace(/\s+/g, ' ').trim().toLowerCase();
            var wantedEnd = String(data.end || '').replace(/\s+/g, ' ').trim().toLowerCase();
            var wantedText = String(data.text || '').replace(/\s+/g, ' ').trim().toLowerCase();
            if (!wantedStart) return fallbackPage || 0;

            var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null);
            var stream = '';
            var positions = [];
            var node;
            var pendingSpace = false;
            while ((node = walker.nextNode())) {
              var source = node.textContent || '';
              for (var i = 0; i < source.length; i++) {
                var ch = source.charAt(i);
                if (/\s/.test(ch)) {
                  if (!pendingSpace && stream.length) {
                    stream += ' ';
                    positions.push({ node: node, offset: i });
                    pendingSpace = true;
                  }
                } else {
                  stream += ch.toLowerCase();
                  positions.push({ node: node, offset: i });
                  pendingSpace = false;
                }
              }
            }

            var candidates = [];
            var from = 0;
            while (from < stream.length) {
              var hit = stream.indexOf(wantedStart, from);
              if (hit < 0) break;
              var score = 0;
              var searchEnd = Math.min(stream.length, hit + Math.max(900, wantedText.length + 240));
              if (wantedText) {
                var middle = wantedText.slice(0, Math.min(360, wantedText.length));
                if (middle && stream.indexOf(middle, hit) >= 0) score += 3;
              }
              if (wantedEnd) {
                var endHit = stream.indexOf(wantedEnd, hit + wantedStart.length);
                if (endHit >= 0 && endHit <= searchEnd + 360) score += 5;
              }
              candidates.push({ index: hit, score: score });
              from = hit + Math.max(1, wantedStart.length);
            }
            if (!candidates.length) return fallbackPage || 0;
            candidates.sort(function(a, b) { return b.score - a.score; });
            var match = candidates[0].index;
            if (!positions[match]) return fallbackPage || 0;

            try {
              var range = document.createRange();
              var p = positions[match];
              range.setStart(p.node, p.offset);
              range.setEnd(p.node, Math.min((p.node.textContent || '').length, p.offset + 1));
              var page = pageOfRangeStart(range);
              if (page < 0) {
                range.setEnd(p.node, Math.min((p.node.textContent || '').length, p.offset + 8));
                page = pageOfRangeStart(range);
              }
              range.detach();
              return page >= 0 ? page : (fallbackPage || 0);
            } catch (e) {
              return fallbackPage || 0;
            }
          }

          function pageForTextAnchor(anchor, fallbackPage) {
            if (!anchor || !body) return fallbackPage || 0;
            var rawAnchor = String(anchor);
            var selectedMarker = '__LIVRE_SELECTED_V1__';
            if (rawAnchor.indexOf(selectedMarker) === 0) {
              try {
                var selected = JSON.parse(rawAnchor.slice(selectedMarker.length));
                var sp = String(selected.startPath || '');
                var ep = String(selected.endPath || '');
                var so = parseInt(selected.startOffset || 0, 10);
                var eo = parseInt(selected.endOffset || 0, 10);
                function resolveSelectedPoint(path, offset) {
                  var parts = path.split('/');
                  var last = parts[parts.length - 1] || '';
                  if (last.indexOf('#text:') !== 0) return null;
                  var parentPath = parts.slice(0, -1).join('/');
                  var node = document.body;
                  var parentParts = parentPath.split('/');
                  var started = false;
                  for (var pi = 0; pi < parentParts.length; pi++) {
                    var seg = parentParts[pi].split(':');
                    var tag = seg[0];
                    var idx = parseInt(seg[1] || '0', 10);
                    if (tag === 'body') { started = true; continue; }
                    if (!started) continue;
                    var kids = node.children, seen = 0, found = null;
                    for (var cj = 0; cj < kids.length; cj++) {
                      if (kids[cj].tagName.toLowerCase() === tag) {
                        if (seen === idx) { found = kids[cj]; break; }
                        seen++;
                      }
                    }
                    if (!found) return null;
                    node = found;
                  }
                  var textIndex = parseInt(last.slice(6), 10);
                  if (!isFinite(textIndex) || textIndex < 0) return null;
                  var walker2 = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, null);
                  var direct = [], tn;
                  while ((tn = walker2.nextNode())) if (tn.parentNode === node) direct.push(tn);
                  var target = direct[textIndex];
                  if (!target) return null;
                  var len = (target.textContent || '').length;
                  return { node: target, offset: Math.max(0, Math.min(offset, len)) };
                }
                var startPoint = resolveSelectedPoint(sp, so);
                var endPoint = resolveSelectedPoint(ep, eo);
                if (startPoint && endPoint) {
                  var range = document.createRange();
                  range.setStart(startPoint.node, startPoint.offset);
                  range.setEnd(endPoint.node, endPoint.offset);
                  var page = pageOfRangeStart(range);
                  range.detach();
                  if (page >= 0) return page;
                }
              } catch (e) {}
            }
            var wanted = rawAnchor.replace(/\s+/g,' ').trim().toLowerCase();
            if (!wanted) return fallbackPage || 0;

            // Build one normalized DOM text stream and remember the exact text-node
            // position represented by every normalized character. This lets the
            // bookmark survive font, margin, line-height and pagination changes.
            // We then locate the actual occurrence and ask the browser for the
            // rendered page of that occurrence; no stored page number is used as
            // the primary location.
            var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null);
            var stream = '';
            var positions = [];
            var node;
            var pendingSpace = false;
            while ((node = walker.nextNode())) {
              var text = node.textContent || '';
              for (var i = 0; i < text.length; i++) {
                var ch = text.charAt(i);
                if (/\s/.test(ch)) {
                  if (!pendingSpace && stream.length) {
                    stream += ' ';
                    positions.push({ node: node, offset: i });
                    pendingSpace = true;
                  }
                } else {
                  stream += ch.toLowerCase();
                  positions.push({ node: node, offset: i });
                  pendingSpace = false;
                }
              }
            }

            var match = stream.indexOf(wanted);
            if (match < 0) {
              // A long whole-page snippet may contain punctuation/spacing that
              // changed at an inline DOM boundary. A shorter prefix remains a
              // useful semantic fallback without returning to page-number logic.
              var prefix = wanted.slice(0, Math.min(80, wanted.length));
              match = prefix ? stream.indexOf(prefix) : -1;
            }
            if (match < 0 || !positions[match]) return fallbackPage || 0;

            try {
              var range = document.createRange();
              range.setStart(positions[match].node, positions[match].offset);
              range.collapse(true);
              var page = pageOfRangeStart(range);
              range.detach();
              return page >= 0 ? page : (fallbackPage || 0);
            } catch (e) {
              return fallbackPage || 0;
            }
          }

          function init() {
            body = document.body;

            // Chromium may horizontally auto-scroll a CSS-column layout while a
            // selection is being extended. Start the page lock on the first
            // non-empty selection change so the internal column boundary never
            // becomes visible. The lock is horizontal only and does not interfere
            // with moving the selection vertically within the current page.
            document.addEventListener('selectionchange', function () {
              try {
                var sel = window.getSelection ? window.getSelection() : null;
                if (sel && sel.rangeCount && sel.toString().trim()) {
                  if (!selectionPageLockActive) setSelectionPageLock(true, currentPage());
                } else if (selectionPageLockActive) {
                  setSelectionPageLock(false);
                }
              } catch (e) {}
            });

            if (!body) {
              setTimeout(init, 30);
              return;
            }

            apply();

            window.Caesura = {
              apply: apply,
              pageCount: pageCount,
              currentPage: currentPage,
              gotoPage: gotoPage,
              nextPage: nextPage,
              prevPage: prevPage,
              ratio: ratio,
              gotoElementById: gotoElementById,
              pageForElementById: pageForElementById,
              pageForTextAnchor: pageForTextAnchor,
              pageForWholePageAnchor: pageForWholePageAnchor,
              pageSnippet: pageSnippet,
              setSelectionPageLock: setSelectionPageLock,
              highlightPageById: highlightPageById,
              gotoHighlightById: gotoHighlightById
            };
          }

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init, { once: true });
          } else {
            init();
          }

          window.addEventListener('load', function () {
            if (window.Caesura) window.Caesura.apply();
          });

          window.addEventListener('resize', function () {
            if (window.Caesura) window.Caesura.apply();
          });
        })();
        </script>
        """.trimIndent()
    }
}
