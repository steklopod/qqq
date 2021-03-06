package ru.decathlon.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.decathlon.model.Comment;
import ru.decathlon.model.Post;
import ru.decathlon.model.PostEditDto;
import ru.decathlon.model.User;
import ru.decathlon.service.AlreadyVotedException;
import ru.decathlon.service.PostService;
import ru.decathlon.service.UserService;
import ru.decathlon.utils.JsonUtils;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class PostsController {

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    @RequestMapping(value = {"/", "/posts"}, method = RequestMethod.GET)
    public String showPostsList(@RequestParam(value = "page", defaultValue = "0") Integer pageNumber, ModelMap model) {
        Page<Post> postsPage = postService.getPostsPage(pageNumber, 10);

        model.addAttribute("postsPage", postsPage);

        User currentUser = userService.currentUser();
        if (currentUser != null)
            model.addAttribute("userId", currentUser.getId());

        return "posts";
    }

    @RequestMapping(value = {"/posts"}, method = RequestMethod.GET, headers="Accept=application/json", produces = "application/json;charset=UTF-8")
    public @ResponseBody String getPostsList(@RequestParam(value = "page", defaultValue = "0") Integer pageNumber) {
        List<Post> posts = postService.getPostsList(pageNumber, 10);

        return "[" + posts.stream().map(this::toJsonLink).collect(Collectors.joining(", \n")) + "]";
    }

    @RequestMapping(value = {"/posts/top"}, method = RequestMethod.GET, headers="Accept=application/json", produces = "application/json;charset=UTF-8")
    public @ResponseBody String getTopPostsList() {
        List<Post> posts = postService.getTopPostsList();

        return "[" + posts.stream().map(this::toJsonLink).collect(Collectors.joining(", \n")) + "]";
    }


    @RequestMapping(value = "/posts/{postId}", method = RequestMethod.GET)
    public String showPost(@PathVariable("postId") Long postId, ModelMap model) {
        Post post = postService.getPost(postId);

        if (post == null)
            throw new ResourceNotFoundException();

        if (post.isHidden() && !userService.isAdmin())
            throw new ResourceNotFoundException();

        model.addAttribute("post", post);

        if (userService.isAuthenticated()) {
            model.addAttribute("comment", new Comment());
        }

        User currentUser = userService.currentUser();
        if (currentUser != null)
            model.addAttribute("userId", currentUser.getId());

        return "post";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/create", method = RequestMethod.GET)
    public String showCreatePostForm(ModelMap model) {
        model.addAttribute("post", new PostEditDto());

        model.addAttribute("edit", false);

        return "editpost";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/create", method = RequestMethod.POST)
    public String createPost(ModelMap model, @Valid @ModelAttribute("post") PostEditDto post, BindingResult result) {
        if (result.hasErrors()) {
            model.addAttribute("edit", false);

            return "editpost";
        }

        postService.saveNewPost(post);

        return "redirect:/posts";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/{postId}/edit", method = RequestMethod.GET)
    public String showEditPostForm(@PathVariable("postId") Long postId, ModelMap model) {
        PostEditDto post = postService.getEditablePost(postId);

        if (post == null)
            throw new ResourceNotFoundException();

        model.addAttribute("post", post);

        model.addAttribute("edit", true);

        return "editpost";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/{postId}/edit", method = RequestMethod.POST)
    public String updatePost(ModelMap model, @Valid @ModelAttribute("post") PostEditDto post, BindingResult result,
                                   @PathVariable("postId") Long postId) {
        post.setId(postId);

        if (result.hasErrors()) {
            model.addAttribute("edit", true);

            return "editpost";
        }

        postService.updatePost(post);

        return "redirect:/posts/" + postId;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/{postId}/hide", method = RequestMethod.POST)
    public @ResponseBody String hidePost(@PathVariable("postId") Long postId) {
        postService.setPostVisibility(postId, true);

        return "ok";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/{postId}/unhide", method = RequestMethod.POST)
    public @ResponseBody String unhidePost(@PathVariable("postId") Long postId) {
        postService.setPostVisibility(postId, false);

        return "ok";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/posts/{postId}/delete", method = RequestMethod.POST)
    public @ResponseBody String deletePost(@PathVariable("postId") Long postId) {
        postService.deletePost(postId);

        return "ok";
    }

    private String toJsonLink(Post post) {
        return "{" + JsonUtils.toJsonField("id", post.getId().toString()) + ", " + JsonUtils.toJsonField("title", post.getTitle()) + "}";
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    @RequestMapping(value = "/posts/{postId}/like", method = RequestMethod.POST)
    public @ResponseBody String like(@PathVariable("postId") Long postId) {
        try {
            postService.vote(postId, true);
        } catch (AlreadyVotedException e) {
            return "already_voted";
        }

        return "ok";
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    @RequestMapping(value = "/posts/{postId}/dislike", method = RequestMethod.POST)
    public @ResponseBody String dislike(@PathVariable("postId") Long postId) {
        try {
            postService.vote(postId, false);
        } catch (AlreadyVotedException e) {
            return "already_voted";
        }

        return "ok";
    }
}