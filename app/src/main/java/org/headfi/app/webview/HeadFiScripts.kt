package org.headfi.app.webview

object HeadFiScripts {

    /**
     * JavaScript injected on page finish to:
     * 1. Automatically check the "Stay logged in" (`remember`) checkbox on any Head-Fi login form
     *    or modal overlay so the `xf_user` cookie is always issued for long-term persistence.
     * 2. Inspect XenForo 2.3's DOM and `XF.config` to report login state, username, avatar,
     *    unread alerts, and unread conversations back to the native Material 3 shell.
     * 3.Optionally apply a clean dark/AMOLED stylesheet and hide intrusive ad placeholders.
     */
    fun buildPageEnhancementScript(
        enableDarkCss: Boolean,
        amoledBlack: Boolean,
        adBlockerEnabled: Boolean
    ): String {
        val bgPrimary = if (amoledBlack) "#000000" else "#111318"
        val bgSurface = if (amoledBlack) "#0A0C10" else "#1B1E26"
        val bgElevated = if (amoledBlack) "#12151C" else "#232731"
        val borderSubtle = if (amoledBlack) "#1F2430" else "#2E3340"

        return """
        (function() {
            try {
                // 1. Auto-enable "Stay logged in" on login forms & XenForo overlays
                function enforceRememberMe() {
                    var checkboxes = document.querySelectorAll('input[name="remember"], input[type="checkbox"][name*="remember"]');
                    for (var i = 0; i < checkboxes.length; i++) {
                        if (!checkboxes[i].checked) {
                            checkboxes[i].checked = true;
                            checkboxes[i].setAttribute('checked', 'checked');
                        }
                    }
                }
                enforceRememberMe();

                // Observe dynamically opened XenForo login overlays
                if (!window.__headfiObserverAttached && window.MutationObserver) {
                    window.__headfiObserverAttached = true;
                    var observer = new MutationObserver(function() {
                        enforceRememberMe();
                    });
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true });
                    }
                }

                // 1b. Report document scrolling so the native smart auto-hiding
                //     toolbars keep working even when the WebView view itself does
                //     not emit scroll changes (fixed layouts / custom scrollers).
                if (!window.__headfiScrollAttached && window.addEventListener) {
                    window.__headfiScrollAttached = true;
                    window.__headfiLastScrollY = Math.round(window.scrollY || window.pageYOffset || 0);
                    var scrollPending = false;
                    window.addEventListener('scroll', function() {
                        if (scrollPending) { return; }
                        scrollPending = true;
                        var report = function() {
                            scrollPending = false;
                            var y = Math.round(window.scrollY || window.pageYOffset || 0);
                            var prev = window.__headfiLastScrollY;
                            if (y !== prev) {
                                window.__headfiLastScrollY = y;
                                if (window.HeadFiBridge && window.HeadFiBridge.onPageScrolled) {
                                    window.HeadFiBridge.onPageScrolled(y, prev);
                                }
                            }
                        };
                        if (window.requestAnimationFrame) {
                            window.requestAnimationFrame(report);
                        } else {
                            report();
                        }
                    }, { passive: true });
                }

                // 2. Extract XenForo 2.3 session state for the native M3 UI
                var htmlEl = document.documentElement;
                var loggedInAttr = htmlEl ? htmlEl.getAttribute('data-logged-in') : 'false';
                var isLoggedIn = (loggedInAttr === 'true') || (window.XF && window.XF.config && window.XF.config.userId > 0);

                var username = '';
                var avatarUrl = '';
                var unreadAlerts = 0;
                var unreadConvs = 0;

                var accountEl = document.querySelector('.p-navgroup-link--user .p-navgroup-linkText');
                if (accountEl && accountEl.textContent) {
                    username = accountEl.textContent.trim();
                }
                var avatarImg = document.querySelector('.p-navgroup-link--user .avatar img, .avatar--xxs img');
                if (avatarImg && avatarImg.src) {
                    avatarUrl = avatarImg.src;
                }

                if (window.XF && window.XF.config && window.XF.config.visitorCounts) {
                    unreadAlerts = parseInt(window.XF.config.visitorCounts.alerts_unviewed || '0', 10) || 0;
                    unreadConvs = parseInt(window.XF.config.visitorCounts.conversations_unread || '0', 10) || 0;
                } else {
                    var alertBadge = document.querySelector('.p-navgroup-link--alerts');
                    if (alertBadge) {
                        unreadAlerts = parseInt(alertBadge.getAttribute('data-badge') || '0', 10) || 0;
                    }
                    var convBadge = document.querySelector('.p-navgroup-link--conversations');
                    if (convBadge) {
                        unreadConvs = parseInt(convBadge.getAttribute('data-badge') || '0', 10) || 0;
                    }
                }

                if (window.HeadFiBridge && window.HeadFiBridge.onXenForoStateDetected) {
                    window.HeadFiBridge.onXenForoStateDetected(
                        Boolean(isLoggedIn),
                        username || '',
                        avatarUrl || '',
                        unreadAlerts,
                        unreadConvs
                    );
                }

                // 3. Inject custom CSS for Ad-Placeholder Cleanup & Dark Mode
                var styleId = 'headfi-native-m3-style';
                var existingStyle = document.getElementById(styleId);
                if (!existingStyle) {
                    existingStyle = document.createElement('style');
                    existingStyle.id = styleId;
                    (document.head || document.documentElement).appendChild(existingStyle);
                }

                var cssRules = '';

                if ($adBlockerEnabled) {
                    cssRules += `
                        #ultra_leaderboard_1, #ultra_leaderboard_2,
                        #sidekick_mobile_1, #sidekick_mobile_2,
                        .head-fi_spnsr_dfp_takeover_layer,
                        .head-fi_spnsr_flxbx_sdbr,
                        .head-fi_spnsr_flxbx_bnnr,
                        .head-fi_spnsr_flxbx_flld,
                        .js-installPromptContainer,
                        .offCanvasMenu-installBanner {
                            display: none !important;
                        }
                    `;
                }

                // Head-Fi's own header is intentionally left alone. The app used to
                // offer a "hide the site header" option, but the site's header is part
                // of how Head-Fi presents itself and hiding it produced a half-hidden
                // result (a second logo lives in the sticky nav).

                // Do not override the Cloudflare Turnstile bot challenge widget if active
                var isBotChallenge = document.body && document.body.getAttribute('data-template') === 'hfbot_challenge_widget';
                if ($enableDarkCss && !isBotChallenge) {
                    cssRules += `
                        :root {
                            color-scheme: dark !important;
                        }
                        html, body, .p-pageWrapper, .p-body, .p-body-inner, #mainbody-actual {
                            background-color: $bgPrimary !important;
                            color: #E3E6EE !important;
                        }
                        .block-container, .message-inner, .message-cell, .message-cell--main,
                        .message-cell--user, .structItemContainer, .structItem, .node-body,
                        .block-body, .block-row, .p-nav, .p-sectionLinks, .menu-content,
                        .offCanvasMenu-content, .overlay-container, .overlay, .p-footer,
                        .head-fi_background-solid, .bbCodeBlock, .bbCodeBlock-content,
                        .bbCodeBlock-title, .input, textarea, select, .fr-box, .fr-wrapper, .fr-toolbar {
                            background-color: $bgSurface !important;
                            color: #E3E6EE !important;
                            border-color: $borderSubtle !important;
                        }
                        .block-header, .block-minorHeader, .block-filterBar, .p-title,
                        .message-attribution, .tabs--standalone {
                            background-color: $bgElevated !important;
                            background-image: none !important;
                            color: #F2F4F8 !important;
                            border-color: $borderSubtle !important;
                        }
                        a:not(.button) {
                            color: #93B8FF !important;
                        }
                        .p-title-value, .structItem-title a, .node-title a, h1, h2, h3, h4 {
                            color: #F3F5FA !important;
                        }
                        .u-muted, .structItem-minor, .node-description, .pairs > dt {
                            color: #9AA2B2 !important;
                        }
                    `;
                }

                existingStyle.textContent = cssRules;
            } catch (err) {
                console.log('HeadFi M3 script error:', err);
            }
        })();
        """.trimIndent()
    }
}
