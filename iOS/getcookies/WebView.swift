//
//  WebView.swift
//  getcookies
//
//  Created by SMG on 4/17/26.
//

import SwiftUI
import WebKit
import Combine

final class WebViewStore: ObservableObject {
    let webView: WKWebView

    init() {
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.websiteDataStore = .default()

        let prefs = WKPreferences()
        prefs.javaScriptCanOpenWindowsAutomatically = true
        config.preferences = prefs

        self.webView = WKWebView(frame: .zero, configuration: config)
        self.webView.allowsBackForwardNavigationGestures = true
    }
}

struct WebView: UIViewRepresentable {
    @Binding var selectedURL: String
    let webViewStore: WebViewStore
    let onPageChanged: (String) -> Void
    let onMagnetLinkTapped: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            parent: self,
            onPageChanged: onPageChanged,
            onMagnetLinkTapped: onMagnetLinkTapped
        )
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = webViewStore.webView
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator

        if let url = URL(string: selectedURL), webView.url == nil {
            webView.load(URLRequest(url: url))
        }

        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard let targetURL = URL(string: selectedURL) else { return }

        if webView.url?.absoluteString != targetURL.absoluteString {
            webView.load(URLRequest(url: targetURL))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        let parent: WebView
        let onPageChanged: (String) -> Void
        let onMagnetLinkTapped: (String) -> Void

        init(
            parent: WebView,
            onPageChanged: @escaping (String) -> Void,
            onMagnetLinkTapped: @escaping (String) -> Void
        ) {
            self.parent = parent
            self.onPageChanged = onPageChanged
            self.onMagnetLinkTapped = onMagnetLinkTapped
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            onPageChanged(webView.url?.absoluteString ?? "")
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            if let url = navigationAction.request.url?.absoluteString,
               url.lowercased().hasPrefix("magnet:?") {
                onMagnetLinkTapped(url)
                decisionHandler(.cancel)
                return
            }

            decisionHandler(.allow)
        }
    }
}
