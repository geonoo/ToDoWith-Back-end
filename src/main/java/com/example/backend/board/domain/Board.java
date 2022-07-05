package com.example.backend.board.domain;

import com.example.backend.board.dto.BoardRequestDto;
import com.example.backend.common.BaseTime;
import com.example.backend.todo.domain.Todo;
import com.example.backend.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Board extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;


    @Column
    private String title;

    @Column
    private String content;

    @Column
    @Enumerated(EnumType.STRING)
    private Category category;

    @Column
    private Long todoId;

    @Column
    private String imageUrl;

    @OneToMany(mappedBy = "board")
    private List<Todo> todo;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User user;


    public Board(BoardRequestDto requestDto, User user) {
        this.category = requestDto.getCategory();
        this.content = requestDto.getContent();
        this.title = requestDto.getTitle();
        this.todoId = requestDto.getTodoId();
        this.user = user;
//        this.imageUrl = requestDto.getImage();
    }

    public void update(BoardRequestDto requestDto, User user) {
        this.title = requestDto.getTitle();
        this.content = requestDto.getContent();
        this.todoId = requestDto.getTodoId();
        this.category = requestDto.getCategory();
        this.user = user;
//        this.imageUrl = requestDto.getImage();
    }


//    연관관계 매핑 시 게시글 삭제와 함께 연관된 개개인의 todo-list 도 함께 삭제됨
//    @OneToMany(mappedBy = "board")
//    private List<Todo> todoList;

}