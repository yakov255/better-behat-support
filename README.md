# Behat external file handling enhancements

![Build](https://github.com/yakov255/better-behat-support/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

<!-- Plugin description -->

Features:  
- [x] Support line "ответ содержит данные значения, аналогичные файлу (.+)"
- [x] Support folder "expected_responses"
- [x] Change filename in gherkin step when file renamed
- [x] Check that file exists or show quick fix "create file $filename"
- [x] Go to file context action
- [x] Find usages on file

### TODO:
- [ ] Add support for safe delete
- [ ] Add support for any folder
- [ ] Add setting with custom steps regexes

### Screens

#### Go to file
![Go to file context action](screens/go_to_file.png)

#### Find usages
![Find usages on file](screens/find_usages.png)

#### File not found inspections

![File not found inspections](screens/file_not_found_inspection.png)

#### Create file refactoring

![Create file refactoring](screens/crate_file_refactoring.png)

<!-- Plugin description end -->