package de.aditu.bookscan

import de.aditu.bookscan.service.*
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.slf4j.LoggerFactory
import org.springframework.context.support.AbstractApplicationContext


@SpringBootApplication
open class BookscanApplication(@Autowired private val args: ApplicationArguments,
                          @Autowired private val applicationContext: AbstractApplicationContext,
                          @Autowired private val dnbBookImporter: DnbBookImporter,
                          @Autowired private val dnbAutorImporter: DnbAutorImporter,
                          @Autowired private val blogPreScan: BlogPreScan,
                          @Autowired private val blogScan: BlogScan,
                          @Autowired private val bookScan: BookScan,
                          @Autowired private val amazonFetcher: AmazonFetcher,
                          @Autowired private val statsPrepare: StatsPrepare,
                          @Autowired private val stats: Stats,
                          @Autowired private val export: Export,
                          @Autowired private val import: Import) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun run(vararg arguments: String?) {
        when {
            args.containsOption("importbooks") && args.getOptionValues("importbooks").isNotEmpty() -> dnbBookImporter.import(args.getOptionValues("importbooks")[0])
            args.containsOption("importauthors") && args.getOptionValues("importauthors").isNotEmpty() -> dnbAutorImporter.import(args.getOptionValues("importauthors")[0])
            args.containsOption("prescan") && args.getOptionValues("prescan").isNotEmpty() -> blogPreScan.scan(args.getOptionValues("prescan")[0])
            args.containsOption("blogscan") && args.getOptionValues("blogscan").isNotEmpty() -> blogScan.scan(args.getOptionValues("blogscan")[0])
            args.containsOption("bookscan") -> bookScan.scan()
            args.containsOption("amazon") -> amazonFetcher.start()
            args.containsOption("statsprepare") -> statsPrepare.start()
            args.containsOption("stats") -> stats.stats()
            args.containsOption("export") && args.getOptionValues("export").isNotEmpty() -> export.start(args.getOptionValues("export")[0])
            args.containsOption("import") && args.containsOption("file") && args.getOptionValues("import").isNotEmpty() && args.getOptionValues("file").isNotEmpty() -> import.start(args.getOptionValues("import")[0], args.getOptionValues("file")[0])
            else -> log.info("no option given")
        }
        SpringApplication.exit(applicationContext)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(BookscanApplication::class.java, *args)
}
