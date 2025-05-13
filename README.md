# ZenithProxy Villager Trader Plugin

Automatically buys items from villagers in exchange for emeralds.

Includes automatic restocking, storing, and configurable to any type of villager and buy item.

## Usage

You need the following setup ingame:

1. A villager trading hall. Its best to be compact - the plugin won't go searching for villagers outside render distance.
1. A chest to restock emeralds or emerald blocks from. You can set up a hopper system to constantly refill this chest.
1. A chest to store the items bought from trades. You can set up a hopper system to transfer items out to larger storage systems.

### Commands

* `trader on/off`
* `trader professions add/del <profession>` -> villager types to trade with
* `trader professions clear`
* `trader buyItems add/del <item name>` -> item(s) to buy from villagers
* `trader buyItems clear`
* `trader restockChest <x> <y> <z>` 
* `trader storeChest <x> <y> <z>`
* `trader restockStacks <int>` -> If restocking emerald blocks keep this around low, around 4 is fine

### Actions Loop

This module is intended to be run continuously. 

It will only stop in the following cases:

1. No emeralds remain in the restock chest
2. No villagers are found.
3. Unable to deposit bought items in the store chest. Could happen if the chest is full.

If villagers are present but their trades are out of stock, it will go into a temporary waiting state and continue after.

## Thanks

Special thanks to @Devin for providing a reference trading module and explanation
