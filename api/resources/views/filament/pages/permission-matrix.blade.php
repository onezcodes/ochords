<x-filament-panels::page>
    <form wire:submit="save" class="fi-section rounded-xl bg-white shadow-sm ring-1 ring-gray-950/5 dark:bg-gray-900 dark:ring-white/10 overflow-x-auto">
        <table class="w-full text-sm">
            <thead>
                <tr class="border-b border-gray-200 dark:border-white/10">
                    <th class="sticky left-0 bg-white dark:bg-gray-900 p-3 text-left font-medium">Permission</th>
                    @foreach ($this->roleNames() as $role)
                        <th class="p-3 text-center font-medium whitespace-nowrap">{{ $role }}</th>
                    @endforeach
                </tr>
            </thead>
            <tbody>
                @foreach ($this->permissionNames() as $permission)
                    <tr class="border-b border-gray-100 dark:border-white/5">
                        <td class="sticky left-0 bg-white dark:bg-gray-900 p-3 font-mono text-xs">{{ $permission }}</td>
                        @foreach ($this->roleNames() as $role)
                            <td class="p-3 text-center">
                                <input
                                    type="checkbox"
                                    wire:model="matrix.{{ $role }}.{{ $permission }}"
                                    @disabled($role === 'super_admin')
                                    class="rounded border-gray-300 text-amber-600 focus:ring-amber-500"
                                />
                            </td>
                        @endforeach
                    </tr>
                @endforeach
            </tbody>
        </table>
        <div class="p-4 flex justify-end">
            <x-filament::button type="submit">
                Save matrix
            </x-filament::button>
        </div>
    </form>
</x-filament-panels::page>
