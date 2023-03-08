MCGPT
================
### A mod that provides a ChatGPT interface inside minecraft

[![Modrinth](https://img.shields.io/modrinth/dt/mcchatgpt?color=00AF5C&label=downloads&logo=modrinth)](https://modrinth.com/mod/mcchatgpt)
[![CurseForge](https://cf.way2muchnoise.eu/full_821752_downloads.svg)](https://curseforge.com/minecraft/mc-mods/mc-chat-gpt)

### This model knows it's inside Minecraft, and will respond accordingly.
- The model is limited by ChatGPT's training data, which ended around when 1.17 was released.

### Commands
- Use `/mcgpt-auth <token>` with your OpenAI token to authenticate with the API
  - You can get a token from [OpenAI API Keys](https://platform.openai.com/account/api-keys)
- Use `/ask <question>` to ask the model a question, the model will respond in chat with context from the last 10 messages in the chat.
- Use `/nextconversation` to start a new conversation with the model or go to the next conversation if `/previousconversation` has been used
- Use `/previousconversation` to go back to the previous conversation with the model

### Installation
#### Requires Architectury
1. Download the latest version of the mod from the [releases page](https://www.curseforge.com/minecraft/mc-mods/mc-chat-gpt/files)
2. Download the latest version of [Architectury](https://www.curseforge.com/minecraft/mc-mods/architectury)
   - If you are using the fabric version, please pownload the latest version of [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
3. Place the downloaded mod files in your mods folder
4. Launch Minecraft

### Reporting Bugs
If you find any bugs, please report them on the [issue tracker](https://github.com/Benjamin-Norton/MCGPT/issues).