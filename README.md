# 📈 PlayerExchange Lite
Developed by **Red Velvet Studios**

PlayerExchange Lite (PX Lite) is a standalone economy plugin for Minecraft 1.21 that turns players into liquid assets. It introduces a dynamic stock market where you can trade shares of other players based on their performance and market demand.

## 🚀 Key Features
- **Standalone Economy:** No Vault or external economy plugins required. It uses an internal wallet system.
- **Visual Trends:** Dynamic Unicode charts (▂▃▄▅) and modern gradients directly in chat.
- **Dynamic Valuation:** Prices change automatically based on player kills, deaths, and trading volume.
- **Built for Speed:** Uses Async SQLite with HikariCP to ensure zero impact on your server TPS.
- **One-Click Trading:** Interactive chat buttons for instant buying and selling.

## 🎮 Commands
- `/px price <player>` : Check the current market value of a player.
- `/px buy <player> <amount>` : Buy shares of a specific player.
- `/px sell <player> <amount>` : Sell your shares back to the market.
- `/px balance` : Check your current PX wallet balance.
- `/px portfolio` : View all the shares you currently own.

### 🛡️ Admin Commands
- `/pxadmin setprice <player> <value>` : Manually override a player's stock price.
- `/pxadmin addmoney <player> <amount>` : Add money to a player's PX wallet.
- `/pxadmin reload` : Refresh the config and messages files.

## 🔑 Permissions
- `px.use` : Access to all basic player commands.
- `px.admin` : Access to all pxadmin management commands.

## 📈 Market Logic
The market is driven by player actions:
- **Kills:** Increases stock value by 5% per kill.
- **Deaths:** Decreases stock value by 10% per death.
- **Base Price:** Every player starts at a default value of $10.00.
- **Limits:** Prices cannot drop below $1.00 to maintain market stability.

## 🛠️ Installation & Build
1. Drop the `PlayerExchange-Lite.jar` into your `/plugins` folder.
2. Restart your server to generate the configuration files.
3. Configure your starting balance and tax rates in `config.yml`.

To build from source:
- Use `./gradlew build`

**Note:** We are currently working on **PX Enterprise**, which will include Inventory GUIs, MySQL support, and Discord integration. 

Support: Still working on
Download: [Modrinth Page](https://modrinth.com/plugin/px-lite-the-next-gen-player-stock-market-1.21)
