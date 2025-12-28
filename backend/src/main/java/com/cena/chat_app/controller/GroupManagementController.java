package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.*;
import com.cena.chat_app.dto.response.GroupEventResponse;
import com.cena.chat_app.service.GroupManagementService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupManagementController {
    private final GroupManagementService groupManagementService;

    public GroupManagementController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    @PostMapping("/leave")
    public ApiResponse<GroupEventResponse> leaveGroup(@RequestBody LeaveGroupRequest request) {
        return groupManagementService.leaveGroup(request);
    }

    @PostMapping("/kick")
    public ApiResponse<GroupEventResponse> kickMember(@RequestBody KickMemberRequest request) {
        return groupManagementService.kickMember(request);
    }

    @PutMapping("/role")
    public ApiResponse<GroupEventResponse> changeRole(@RequestBody ChangeRoleRequest request) {
        return groupManagementService.changeRole(request);
    }

    @PostMapping("/transfer-ownership")
    public ApiResponse<GroupEventResponse> transferOwnership(@RequestBody TransferOwnershipRequest request) {
        return groupManagementService.transferOwnership(request);
    }

    @PutMapping("/info")
    public ApiResponse<GroupEventResponse> updateGroupInfo(@RequestBody UpdateGroupInfoRequest request) {
        return groupManagementService.updateGroupInfo(request);
    }
}
