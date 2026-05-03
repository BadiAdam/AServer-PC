package com.badiadam.aserver

import java.io.File

object AppConfig {
    // Kullanıcının sistemine göre güvenli ana klasörü belirler (Windows, Mac, Linux)
    val BASE_DIR: File by lazy {
        val userHome = System.getProperty("user.home")
        // Eğer ilerde değiştirmek istersen sadece burayı değiştirmen yetecek
        File(userHome, "AServerData") 
    }
    
    // Sunucuların tutulacağı klasör
    val SERVERS_DIR: File by lazy {
        File(BASE_DIR, "servers").apply { mkdirs() } // Yoksa oluştur
    }
    
    // Ortak (Global) Java klasörümüz (AutoJava için)
    val JAVA_DIR: File by lazy {
        File(BASE_DIR, "java").apply { mkdirs() }
    }
}