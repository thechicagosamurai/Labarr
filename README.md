# Labarr - Homelab Management App

A powerful Android application designed specifically for homelab enthusiasts to manage and access their self-hosted services with enhanced security and convenience.

![Labarr App](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Security](https://img.shields.io/badge/Security-Enabled-green?style=for-the-badge)

## 🌟 Features

### 🔐 **Advanced Security**
- **PIN Protection**: Secure your credentials with a customizable PIN
- **Encrypted Storage**: All sensitive data is encrypted using Android Keystore
- **Session Management**: PIN verification persists during app sessions
- **Secure Credential Manager**: Encryption for stored credentials

### 🏠 **Homelab Integration**
- **Dual URL Support**: Configure both local IP and fallback domain URLs
- **WiFi Network Detection**: Automatically switch between local and remote access
- **Trusted WiFi Networks**: Restrict local access to specific WiFi networks
- **HTTPS Support**: Full support for both HTTP and HTTPS with self-signed certificate handling

### 🌐 **WebView Management**
- **Desktop/Mobile View Toggle**: Switch between desktop and mobile view modes
- **Site-Specific Settings**: Remember view preferences per website
- **Auto-Rotation Control**: Enable/disable screen rotation per site
- **Mixed Content Support**: Handle both HTTP and HTTPS content seamlessly

### 🔑 **Credential Management**
- **Auto-Fill Detection**: Automatically detect and save login credentials
- **Secure Storage**: Encrypted credential storage with PIN protection
- **Credential Categories**: Organize credentials by service/application
- **Easy Access**: Quick access to saved credentials with search functionality

### 📱 **User Experience**
- **Slide-Out Menu**: Intuitive slide-out navigation menu
- **Floating Back Button**: Movable back button with position memory
- **Gesture Controls**: Swipe gestures for navigation
- **Dark Theme**: Modern dark interface design
- **Responsive Layout**: Optimized for various screen sizes

### 🔄 **Data Management**
- **Backup & Restore**: Create encrypted backups of all app data
- **Export/Import**: Backup files can be shared and restored
- **Data Wipe**: Complete data clearing with comprehensive cleanup
- **Cache Management**: Clear WebView cache and storage data

### 🛠 **Advanced Features**
- **Download Management**: Handle file downloads with proper naming
- **Cookie Management**: Persistent login sessions with cookie handling
- **JavaScript Interface**: Enhanced web app integration
- **Error Handling**: Comprehensive error handling and user feedback

## 🚀 Getting Started

### Prerequisites
- Android 7.0 (API level 24) or higher
- Internet connection for remote access
- Local network access for homelab services

### Initial Setup
1. **Configure URLs**: Add your homelab IP address and fallback domain
2. **Set WiFi Network** (Optional): Configure trusted WiFi for local access
3. **Set PIN** (Recommended): Enable PIN protection for enhanced security
4. **Test Connection**: Verify both local and remote access work correctly

## ⚙️ Configuration

### URL Settings
- **Homelab IP**: Your local network IP address (e.g., `192.168.1.100`)
- **Fallback URL**: Your public domain or remote access URL
- **WiFi SSID**: Restrict local access to specific WiFi networks

### Security Settings
- **PIN Protection**: 4-6 digit PIN for app access
- **Login Persistence**: Control whether login sessions persist
- **Auto-Rotation**: Enable/disable screen rotation

### View Settings
- **Desktop View**: Enable desktop view mode for better compatibility
- **Site-Specific**: Remember view preferences per website

## 🔧 Advanced Usage

### Credential Management
1. Navigate to the Credentials section
2. Add new credentials manually or let the app auto-detect
3. Organize credentials by service name
4. Access saved credentials quickly when needed

### Backup & Restore
1. Go to Settings → Backup & Restore
2. Create encrypted backup with PIN protection
3. Store backup files securely
4. Restore from backup when needed

### Troubleshooting
- **Connection Issues**: Check network connectivity and URL configuration
- **SSL Errors**: App automatically handles self-signed certificates
- **Cache Issues**: Use the clear cache function in settings
- **PIN Issues**: Reset PIN through settings if forgotten

## 🛡️ Security Features

### Data Protection
- **Encryption**: AES-256 encryption for all sensitive data
- **KeyStore**: Uses Android Keystore for secure key storage
- **PIN Protection**: Optional PIN for app access
- **Session Security**: Secure session management

### Network Security
- **HTTPS Support**: Full TLS/SSL support with certificate handling
- **Self-Signed Certificates**: Automatic handling of homelab certificates
- **Mixed Content**: Support for mixed HTTP/HTTPS content
- **Network Isolation**: WiFi-based access control

## 📊 Technical Specifications

### System Requirements
- **Android Version**: 7.0 (API 24) or higher
- **Architecture**: ARM64, ARMv7, x86, x86_64
- **RAM**: 100MB minimum
- **Storage**: 50MB minimum

### Permissions
- **Internet**: Required for web access
- **Network State**: For WiFi detection
- **Storage**: For backup/restore functionality
- **Vibration**: For haptic feedback

### Technologies Used
- **Language**: Kotlin
- **UI Framework**: Android Views with Data Binding
- **WebView**: Custom WebView with enhanced features
- **Encryption**: Android Keystore with AES-256
- **Storage**: SharedPreferences and File Storage

## 🤝 Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

### Development Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on device/emulator

## 📄 License

This project is licensed under the GNU General Public License v3.0 (GPLv3) - see the [LICENSE](LICENSE) file for details.

**Important Note**: This software is free and open source. You are free to:
- Use, modify, and distribute the source code
- Create derivative works
- Share your modifications

**However, you must**:
- Keep the source code open and available
- License any derivative works under GPLv3
- Include the original license and copyright notices

**Commercial Use**: While the source code is free, the compiled Android application may be sold on app stores. The GPLv3 license ensures that anyone who redistributes the software must also make the source code available under the same terms.

## 🙏 Acknowledgments

- Built for the homelab community
- Designed with security and usability in mind
- Inspired by the need for better homelab management tools

## 📞 Support

For support, feature requests, or bug reports:
- Open an issue on GitHub
- Check the troubleshooting section above
- Review the configuration guide

---

**Labarr** - Making homelab management simple, secure, and accessible. 🏠🔐 
