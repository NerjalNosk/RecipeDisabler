# RecipeDisabler

~~More like ResourceDisabler~~

A simple Minecraft Fabric mod allowing to
make the game not load some specific
recipes/resources, without having to use
some complex KubeJS/CraftTweaker script
when trying to handle modded recipe types.

For Minecraft 1.18.2+<br>
(Never tested earlier versions,
and probably never will)

This mod is mainly intended to be used in
modpacks or alongside unaltered datapacks.
<br>It is only required to be loaded on
the server side.

Recipes are to be entered in the config
file by ID, with or without the initial
`recipes/` at the beginning of the path.
It is recommended to use
[REI](https://modrinth.com/mod/roughly-enough-items)
with tooltips in order to get the wanted
recipe's ID.