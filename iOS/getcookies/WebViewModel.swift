//
//  WebViewModel.swift
//  getcookies
//
//  Created by SMG on 4/17/26.
//

import Foundation
import SwiftUI
import Combine
import WebKit

@MainActor
final class WebViewModel: ObservableObject {
    @Published var urlList: [String] = [
        "https://nyaa.land/",
        "https://update.spotv24.com",
    ]

    @Published var selectedURL: String = "https://nyaa.land/"
    @Published var currentURL: String = ""
    @Published var showAlert: Bool = false
    @Published var alertTitle: String = ""
    @Published var alertMessage: String = ""

    let webViewStore = WebViewStore()

    // Synology 설정
    private let synologyBaseURL = "https://garajilpung.synology.me:5001"
    private let synologyAccount = "garajilpung"
    private let synologyPassword = "Gara_may'n0"
    private let synologyDownloadFolder = "down"

    // 쿠키 전송 서버
    private let cookiePostURL = "https://garajilpung.synology.me/api/v1/urlcookie.php"

    func loadInitialPage() {
        guard let url = URL(string: selectedURL) else { return }
        if webViewStore.webView.url == nil {
            webViewStore.webView.load(URLRequest(url: url))
        }
    }

    func getCookieInfo() {
        let webView = webViewStore.webView
        let fullURL = webView.url?.absoluteString ?? selectedURL
        let host = URL(string: fullURL)?.host ?? ""
        let userAgent = webView.value(forKey: "userAgent") as? String ?? ""

        fetchCookies(for: fullURL) { [weak self] cookieString in
            guard let self else { return }

            webView.evaluateJavaScript("document.cookie") { result, _ in
                let jsCookie = (result as? String) ?? ""

                let message = """
                현재 URL:
                \(host)

                User-Agent:
                \(userAgent)

                Cookie:
                \(cookieString.isEmpty ? "쿠키 없음" : cookieString)

                [document.cookie]
                \(jsCookie.isEmpty ? "없음" : jsCookie)
                """

                Task { @MainActor in
                    self.showPopup(title: "쿠키 정보", message: message)
                }
            }
        }
    }

    func clearCookies() {
        let webView = webViewStore.webView
        let dataStore = webView.configuration.websiteDataStore

        dataStore.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { [weak self] records in
            dataStore.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: records) {
                webView.reload()

                Task { @MainActor in
                    self?.showPopup(title: "쿠키 삭제", message: "쿠키 및 WebView 데이터 삭제 완료")
                }
            }
        }
    }

    func sendCookie() {
        let webView = webViewStore.webView
        let fullURL = webView.url?.absoluteString ?? selectedURL
        let host = URL(string: fullURL)?.host ?? ""
        let userAgent = webView.value(forKey: "userAgent") as? String ?? ""

        fetchCookies(for: fullURL) { [weak self] cookieString in
            guard let self else { return }

            guard let url = URL(string: self.cookiePostURL) else {
                Task { @MainActor in
                    self.showPopup(title: "쿠키 전송 실패", message: "전송 URL이 잘못되었습니다.")
                }
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.timeoutInterval = 15
            request.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
            request.setValue("application/json", forHTTPHeaderField: "Accept")

            let body: [String: Any] = [
                "url": host,
                "userAgent": userAgent,
                "cookie": cookieString
            ]

            do {
                request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
            } catch {
                Task { @MainActor in
                    self.showPopup(title: "쿠키 전송 실패", message: error.localizedDescription)
                }
                return
            }

            URLSession.shared.dataTask(with: request) { data, response, error in
                if let error {
                    Task { @MainActor in
                        self.showPopup(title: "쿠키 전송 실패", message: error.localizedDescription)
                    }
                    return
                }

                let responseCode = (response as? HTTPURLResponse)?.statusCode ?? -1
                let responseText = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""

                let message = """
                전송 완료

                POST URL:
                \(self.cookiePostURL)

                Response Code:
                \(responseCode)

                Response:
                \(responseText)
                """

                Task { @MainActor in
                    self.showPopup(title: "쿠키 전송", message: message)
                }
            }.resume()
        }
    }

    func sendMagnetToSynology(magnetUrl: String) {
        Task {
            do {
                let sid = try await synologyLogin()
                let responseText = try await createSynologyDownloadTask(sid: sid, magnetUrl: magnetUrl)

                await MainActor.run {
                    self.showPopup(title: "Download Station 등록 완료", message: responseText)
                }
            } catch {
                await MainActor.run {
                    self.showPopup(title: "magnet 전송 실패", message: error.localizedDescription)
                }
            }
        }
    }

    private func fetchCookies(for urlString: String, completion: @escaping (String) -> Void) {
        guard let url = URL(string: urlString) else {
            completion("")
            return
        }

        webViewStore.webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
            let matched = cookies.filter { cookie in
                guard let host = url.host else { return false }
                return host.contains(cookie.domain.replacingOccurrences(of: ".", with: ""))
                    || cookie.domain.contains(host)
            }

            let cookieString = matched
                .map { "\($0.name)=\($0.value)" }
                .joined(separator: "; ")

            completion(cookieString)
        }
    }

    private func showPopup(title: String, message: String) {
        self.alertTitle = title
        self.alertMessage = message
        self.showAlert = true
    }
}

// MARK: - Synology

extension WebViewModel {
    private func synologyLogin() async throws -> String {
        var components = URLComponents(string: "\(synologyBaseURL)/webapi/auth.cgi")!
        components.queryItems = [
            URLQueryItem(name: "api", value: "SYNO.API.Auth"),
            URLQueryItem(name: "version", value: "7"),
            URLQueryItem(name: "method", value: "login"),
            URLQueryItem(name: "account", value: synologyAccount),
            URLQueryItem(name: "passwd", value: synologyPassword),
            URLQueryItem(name: "session", value: "DownloadStation"),
            URLQueryItem(name: "format", value: "sid")
        ]

        guard let url = components.url else {
            throw NSError(domain: "Synology", code: -1, userInfo: [NSLocalizedDescriptionKey: "로그인 URL 생성 실패"])
        }

        let (data, _) = try await URLSession.shared.data(from: url)

        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let success = json["success"] as? Bool,
            success == true,
            let dataDict = json["data"] as? [String: Any],
            let sid = dataDict["sid"] as? String
        else {
            let text = String(data: data, encoding: .utf8) ?? "응답 없음"
            throw NSError(domain: "Synology", code: -2, userInfo: [NSLocalizedDescriptionKey: "Synology 로그인 실패: \(text)"])
        }

        return sid
    }

    private func createSynologyDownloadTask(sid: String, magnetUrl: String) async throws -> String {
        var components = URLComponents(string: "\(synologyBaseURL)/webapi/DownloadStation/task.cgi")!
        components.queryItems = [
            URLQueryItem(name: "api", value: "SYNO.DownloadStation.Task"),
            URLQueryItem(name: "version", value: "1"),
            URLQueryItem(name: "method", value: "create"),
            URLQueryItem(name: "uri", value: magnetUrl),
            URLQueryItem(name: "destination", value: synologyDownloadFolder),
            URLQueryItem(name: "_sid", value: sid)
        ]

        guard let url = components.url else {
            throw NSError(domain: "Synology", code: -3, userInfo: [NSLocalizedDescriptionKey: "다운로드 작업 URL 생성 실패"])
        }

        let (data, _) = try await URLSession.shared.data(from: url)
        let text = String(data: data, encoding: .utf8) ?? ""

        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let success = json["success"] as? Bool,
            success == true
        else {
            throw NSError(domain: "Synology", code: -4, userInfo: [NSLocalizedDescriptionKey: "Download Station 등록 실패: \(text)"])
        }

        return text
    }
}
