MCChatGPT
================
### A mod that provides a ChatGPT interface inside minecraft

[![Modrinth](https://img.shields.io/modrinth/dt/mcchatgpt?color=00AF5C&label=downloads&logo=modrinth)](https://modrinth.com/mod/mcchatgpt)
[![CurseForge](https://cf.way2muchnoise.eu/full_835315_downloads.svg)](https://curseforge.com/minecraft/mc-mods/mcchatgpt)

### This model knows it's inside Minecraft, and will respond accordingly.
- The model is limited by ChatGPT's training data, which ended around when 1.17 was released.

### Commands
- Use `/mcchatgpt-auth <token>` with your OpenAI token to authenticate with the API
  - You can get a token from [OpenAI API Keys](https://platform.openai.com/account/api-keys)
- Use `/ask <question>` to ask the model a question, the model will respond in the chat with context from the last 10 messages.
  - Hover over the message to see the number of tokens used and approximate cost of the API request.
- Use `/setcontextlevel <0-3>` to set the context level of the model.
  - Higher context levels will provide the model with more information about the world and the player but will cost more per API request.
  - 0: No information (default)
  - 1: Player information
    - \+ ~100 tokens per request
  - 2: Player and world information
    - \+ ~200 tokens per request
  - 3: Player, world, and entity information 
    - \+ ~1k tokens per request
- Use `/nextconversation` go to the next conversation, or start a new conversation with the model.
- Use `/previousconversation` to go back to the previous conversation with the model.
- Use `/setconversation <conversationid>` to set the conversation with the model to a specific conversation.
- Use `/listconversations` to list all the conversations you have had with the model.
  - This will provide the conversation id, and the last message you sent in the conversation.

### Config
- model: The model used for the API requests. 
  - Must support Chat Completions otherwise an error will occur.
  - `gpt-3.5-turbo` is the default value.
    - If changed, you may need to change the estimated_cost_per_token value in the config to match the new model.
  - You can find a list of models [here](https://platform.openai.com/docs/models/overview).
- temperature: The temperature used for the API requests.
  - Between 0 and 2, higher values will result in more creative responses, while lower values will result in more deterministic responses.
  - `1.0` is the default value.

### Installation
1. Download the latest version of the mod from the [releases page](https://www.curseforge.com/minecraft/mc-mods/mc-chat-gpt/files)
   - If you are using the fabric version, please download the latest version of [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
2. Place the downloaded mod files in your mods folder
3. Launch Minecraft

### Reporting Bugs
If you find any bugs, please report them on the [issue tracker](https://github.com/Benjamin-Norton/MCGPT/issues).
