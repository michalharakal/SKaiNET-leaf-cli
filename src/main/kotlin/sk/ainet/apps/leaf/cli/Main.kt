@file:OptIn(ExperimentalCli::class)

package sk.ainet.apps.leaf.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

fun main(args: Array<String>) {
    val parser = ArgParser("leaf-cli")
    parser.subcommands(IndexCommand(), AskCommand(), BenchCommand())
    parser.parse(args)
}
