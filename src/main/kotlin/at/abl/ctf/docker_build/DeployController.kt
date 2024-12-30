package at.abl.ctf.docker_build

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriUtils

@Controller
class DeployController(val deployService: DeployService) {

    @PostMapping(value = ["/deploy"], consumes = ["multipart/form-data"])
    fun deploy(
        @RequestParam("zipfile", required = true) zipfile: MultipartFile
    ): RedirectView {
        val url = deployService.deploy(zipfile.bytes)
        return RedirectView(
            "/deployed.html?url=" + UriUtils.encodeQueryParam(
                url, Charsets.UTF_8
            )
        )
    }
}