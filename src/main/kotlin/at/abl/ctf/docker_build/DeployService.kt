package at.abl.ctf.docker_build

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteRecursively
import kotlin.random.Random

@Service
class DeployService(
    @Value("\${url.isHttps}") val isHttps: Boolean,
    @Value("\${url.suffix}") val urlSuffix: String,
) {

    private val executorService = Executors.newVirtualThreadPerTaskExecutor()

    @OptIn(ExperimentalPathApi::class, ExperimentalStdlibApi::class)
    fun deploy(
        zipfile: ByteArray
    ): String {
        val name = Random.nextInt().toHexString()
        //TODO validate zip
        val tmpFolder = Files.createTempDirectory("mystaticfileshoster")
        println(tmpFolder)
        try {
            unzipFile(tmpFolder, zipfile)
            writeDockerFile(tmpFolder)
            buildNewImage(tmpFolder, name)
            val host = name + urlSuffix
            val url = "http${if (isHttps) "s" else ""}://" + host
            runImage(name, host)
            return url
        } catch (e: Exception) {
            throw RuntimeException("error deploying", e)
        } finally {
            tmpFolder.deleteRecursively()
        }
    }

    private fun runImage(name: String, host: String) {
        val dockerName = getDockerName(name)
        executorService.submit {
            println("running image $name")
            val command = mutableListOf("docker", "run", "--rm", "-d", "--name", dockerName)
            command.addAll(getTraefikLabels(name, host))
            command.add(dockerName)
            ProcessBuilder(command)
                .inheritIO()
                .start()
                .waitFor()
        }
        executorService.submit {
            Thread.sleep(10 * 60 * 1000)
            println("shutdown image $name")
            ProcessBuilder("docker", "stop", dockerName)
                .inheritIO()
                .start()
                .waitFor()
        }
    }

    private fun getTraefikLabels(name: String, host: String): Collection<String> {
        val routerName = getDockerName(name)
        if (isHttps) {
            return listOf(
                "-l", "traefik.enable=true",
                "-l", "traefik.http.routers.$routerName.rule=Host(`${host}`)",
                "-l", "traefik.http.routers.$routerName.entrypoints=websecure",
                "-l", "traefik.http.routers.$routerName.tls.certresolver=myresolver"
            )
        } else {
            return listOf(
                "-l", "traefik.enable=true",
                "-l", "traefik.http.routers.$routerName.rule=Host(`${host}`)",
                "-l", "traefik.http.routers.$routerName.entrypoints=web"
            )
        }
    }

    private fun buildNewImage(tmpFolder: Path, name: String) {
        println("building image $name")
        ProcessBuilder("docker", "build", "-t", getDockerName(name), ".")
            .directory(tmpFolder.toFile())
            .inheritIO()
            .start()
            .waitFor()
    }

    private fun getDockerName(name: String) =
        "mystaticsite-$name"


    private fun writeDockerFile(tmpFolder: Path) {
        val dockerfile = this.javaClass.getResourceAsStream("/Dockerfile")!!.readAllBytes()
        Files.write(tmpFolder.resolve("Dockerfile"), dockerfile)
    }

    private fun unzipFile(tmpFolder: Path, zipfile: ByteArray) {
        val dataFolder = tmpFolder.resolve("data")
        dataFolder.createDirectory()
        val zipIn = ZipInputStream(ByteArrayInputStream(zipfile))
        while (true) {
            val nextEntry = zipIn.nextEntry
            if (nextEntry == null) {
                break
            }

            if (nextEntry.isDirectory) {
                throw IllegalArgumentException("folders are not allowed!")
            }

            if(nextEntry.name == ".htaccess"){
                throw IllegalArgumentException("nono, no overriding access control!!!")
            }
            //TODO sanitize name?
            val file = dataFolder.resolve(nextEntry.name)
            Files.write(file, zipIn.readAllBytes())
        }
    }
}