//
//  ContentView.swift
//  getcookies
//
//  Created by SMG on 4/17/26.
//

import SwiftUI
import WebKit

struct ContentView: View {
    @StateObject private var viewModel = WebViewModel()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("URL 목록")
                    .font(.title3)
                    .fontWeight(.bold)

                Spacer()

                Menu {
                    Button("정보 가져오기") {
                        viewModel.getCookieInfo()
                    }

                    Button("쿠키 삭제") {
                        viewModel.clearCookies()
                    }

                    Button("쿠키 전송") {
                        viewModel.sendCookie()
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.title3)
                        .foregroundColor(.primary)
                        .frame(width: 30, height: 30)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)

            Picker("URL 목록", selection: $viewModel.selectedURL) {
                ForEach(viewModel.urlList, id: \.self) { url in
                    Text(url).tag(url)
                }
            }
            .pickerStyle(.menu)
            .padding(.horizontal, 16)
            .padding(.top, 8)

            WebView(
                selectedURL: $viewModel.selectedURL,
                webViewStore: viewModel.webViewStore,
                onPageChanged: { url in
                    viewModel.currentURL = url
                },
                onMagnetLinkTapped: { magnet in
                    viewModel.sendMagnetToSynology(magnetUrl: magnet)
                }
            )
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 16)
        }
        .alert(viewModel.alertTitle, isPresented: $viewModel.showAlert) {
            Button("확인", role: .cancel) { }
        } message: {
            ScrollView {
                Text(viewModel.alertMessage)
            }
        }
        .onAppear {
            viewModel.loadInitialPage()
        }
    }
}
