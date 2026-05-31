# Security Policy

## Supported Versions

Currently this project is an **engineering prototype** and is not ready for production use.
We do not offer guarantees around long-term security support for prototype artifacts.

## Reporting a Vulnerability

If you discover a security vulnerability within this project, please open an issue in the issue tracker or contact the maintainers directly.

## API Key Security

* Never hardcode API keys or secret credentials directly inside source repositories or build files.
* Ensure your `.env`, `debug.keystore.base64`, and similar secret files are added to `.gitignore`.
* Production integrations should utilize runtime keystore-backed secrets or heavily restricted API limits.
